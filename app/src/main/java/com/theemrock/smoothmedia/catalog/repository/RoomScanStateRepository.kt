package com.theemrock.smoothmedia.catalog.repository

import android.database.sqlite.SQLiteConstraintException
import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateState
import com.theemrock.smoothmedia.catalog.database.entity.ScanCheckpointCoverage
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.catalog.database.entity.SourceScanCheckpointEntity
import com.theemrock.smoothmedia.catalog.scan.ScanLeaseRecoveryPolicy

class RoomScanStateRepository(
    private val database: CatalogDatabase,
) : ScanStateRepository {
    private val dao = database.scanDao()

    override fun startSession(
        session: ScanSessionEntity,
        checkpoints: List<SourceScanCheckpointEntity>,
    ) {
        database.runInTransaction {
            dao.insertSession(session)
            dao.acquireGlobalLease(session.sessionId)
            checkpoints.forEach(dao::insertCheckpoint)
        }
    }

    override fun recoverStaleLeaseAndStart(
        session: ScanSessionEntity,
        checkpoints: List<SourceScanCheckpointEntity>,
    ): LeaseRecoveryResult {
        lateinit var result: LeaseRecoveryResult
        database.runInTransaction {
            val lease = dao.findActiveLease()
            val owner = lease?.let { dao.findSession(it.sessionId) }
            if (owner != null && owner.status in RESUMABLE_STATUSES) {
                result = LeaseRecoveryResult.ResumeRequired(owner.sessionId)
            } else {
                val recovered = lease != null
                if (recovered) {
                    dao.clearGlobalLease()
                }
                dao.insertSession(session)
                dao.acquireGlobalLease(session.sessionId)
                checkpoints.forEach(dao::insertCheckpoint)
                result = LeaseRecoveryResult.Started(session.sessionId, recovered)
            }
        }
        return result
    }

    override fun findSession(sessionId: String): ScanSessionEntity? = dao.findSession(sessionId)

    override fun updateCheckpoint(
        sessionId: String,
        sourceId: String,
        continuationToken: String?,
        discoveredCount: Int,
        processedCount: Int,
        coverage: ScanCheckpointCoverage,
        updatedAtEpochMs: Long,
    ): Boolean {
        var updated = false
        database.runInTransaction {
            updated = dao.updateCheckpoint(
                sessionId,
                sourceId,
                continuationToken,
                discoveredCount,
                processedCount,
                coverage,
                updatedAtEpochMs,
            ) == 1
            if (updated) check(dao.touchSession(sessionId, updatedAtEpochMs) == 1)
        }
        return updated
    }

    override fun findCheckpoint(
        sessionId: String,
        sourceId: String,
    ): SourceScanCheckpointEntity? = dao.findCheckpoint(sessionId, sourceId)

    override fun heartbeat(sessionId: String, updatedAtEpochMs: Long): Boolean =
        dao.heartbeat(sessionId, updatedAtEpochMs) == 1

    override fun recordCandidate(candidate: ScanCandidateEntity) = dao.insertCandidate(candidate)

    override fun findCandidate(
        sessionId: String,
        sourceId: String,
        candidateKey: String,
    ): ScanCandidateEntity? = dao.findCandidate(sessionId, sourceId, candidateKey)

    override fun updateCandidateState(
        sessionId: String,
        sourceId: String,
        candidateKey: String,
        state: ScanCandidateState,
        locationId: String?,
        updatedAtEpochMs: Long,
    ): Boolean = dao.updateCandidateState(
        sessionId,
        sourceId,
        candidateKey,
        state,
        locationId,
        updatedAtEpochMs,
    ) == 1

    override fun recoverInterruptedSession(
        sessionId: String,
        updatedAtEpochMs: Long,
        staleAfterMs: Long,
    ): ScanRecoveryResult {
        lateinit var result: ScanRecoveryResult
        database.runInTransaction {
            val session = dao.findSession(sessionId)
            val lease = dao.findActiveLease()
            when {
                session == null -> result = ScanRecoveryResult.Rejected(ScanRecoveryResult.RejectionReason.NOT_FOUND)
                session.status == ScanSessionStatus.CANCELLED ->
                    result = ScanRecoveryResult.Rejected(ScanRecoveryResult.RejectionReason.CANCELLED_SESSION)
                session.status == ScanSessionStatus.COMPLETED || session.status == ScanSessionStatus.FAILED ->
                    result = ScanRecoveryResult.Rejected(ScanRecoveryResult.RejectionReason.TERMINAL_SESSION)
                lease != null && lease.sessionId != sessionId ->
                    result = ScanRecoveryResult.Rejected(ScanRecoveryResult.RejectionReason.LIVE_SESSION)
                lease != null && !ScanLeaseRecoveryPolicy.isStale(
                    nowEpochMs = updatedAtEpochMs,
                    lastProgressAtEpochMs = dao.latestCheckpointUpdatedAt(sessionId) ?: session.updatedAtEpochMs,
                    staleAfterMs = staleAfterMs,
                ) -> result = ScanRecoveryResult.Rejected(ScanRecoveryResult.RejectionReason.LIVE_SESSION)
                else -> {
                    if (lease != null) check(dao.releaseLease(sessionId) == 1)
                    if (session.status == ScanSessionStatus.RUNNING) {
                        check(
                            dao.updateSessionStatus(
                                sessionId,
                                ScanSessionStatus.PAUSED,
                                updatedAtEpochMs,
                                null,
                            ) == 1,
                        )
                    }
                    val reset = dao.resetProcessingCandidates(
                        sessionId = sessionId,
                        processingState = ScanCandidateState.PROCESSING,
                        retryableState = ScanCandidateState.DISCOVERED,
                        updatedAtEpochMs = updatedAtEpochMs,
                    )
                    result = ScanRecoveryResult.Recovered(sessionId, reset)
                }
            }
        }
        return result
    }

    override fun completeSession(sessionId: String, completedAtEpochMs: Long) {
        database.runInTransaction {
            if (dao.completeRunningSessionWithLease(sessionId, completedAtEpochMs) == 1) {
                dao.releaseLease(sessionId)
            }
        }
    }

    override fun pauseSession(sessionId: String, updatedAtEpochMs: Long): ScanLifecycleResult {
        lateinit var result: ScanLifecycleResult
        database.runInTransaction {
            val session = dao.findSession(sessionId)
            when {
                session == null -> result = ScanLifecycleResult.Rejected(ScanLifecycleResult.RejectionReason.NOT_FOUND)
                session.status != ScanSessionStatus.RUNNING ->
                    result = ScanLifecycleResult.Rejected(ScanLifecycleResult.RejectionReason.INVALID_STATE)
                dao.findActiveLease()?.sessionId != sessionId ->
                    result = ScanLifecycleResult.Rejected(ScanLifecycleResult.RejectionReason.LEASE_NOT_OWNED)
                else -> {
                    check(
                        dao.updateSessionStatus(
                            sessionId,
                            ScanSessionStatus.PAUSED,
                            updatedAtEpochMs,
                            null,
                        ) == 1,
                    )
                    dao.releaseLease(sessionId)
                    result = ScanLifecycleResult.Applied(sessionId, ScanSessionStatus.PAUSED)
                }
            }
        }
        return result
    }

    override fun resumeSession(sessionId: String, updatedAtEpochMs: Long): ScanLifecycleResult {
        lateinit var result: ScanLifecycleResult
        database.runInTransaction {
            val session = dao.findSession(sessionId)
            when {
                session == null -> result = ScanLifecycleResult.Rejected(ScanLifecycleResult.RejectionReason.NOT_FOUND)
                session.status != ScanSessionStatus.PAUSED ->
                    result = ScanLifecycleResult.Rejected(ScanLifecycleResult.RejectionReason.INVALID_STATE)
                dao.findActiveLease() != null ->
                    result = ScanLifecycleResult.Rejected(ScanLifecycleResult.RejectionReason.ACTIVE_SCAN_EXISTS)
                else -> {
                    try {
                        dao.acquireGlobalLease(sessionId)
                        check(
                            dao.updateSessionStatus(
                                sessionId,
                                ScanSessionStatus.RUNNING,
                                updatedAtEpochMs,
                                null,
                            ) == 1,
                        )
                        result = ScanLifecycleResult.Applied(sessionId, ScanSessionStatus.RUNNING)
                    } catch (_: SQLiteConstraintException) {
                        result = ScanLifecycleResult.Rejected(ScanLifecycleResult.RejectionReason.ACTIVE_SCAN_EXISTS)
                    }
                }
            }
        }
        return result
    }

    override fun cancelSession(sessionId: String, updatedAtEpochMs: Long): ScanLifecycleResult {
        lateinit var result: ScanLifecycleResult
        database.runInTransaction {
            val session = dao.findSession(sessionId)
            when {
                session == null -> result = ScanLifecycleResult.Rejected(ScanLifecycleResult.RejectionReason.NOT_FOUND)
                session.status !in ACTIVE_OR_PAUSED_STATUSES ->
                    result = ScanLifecycleResult.Rejected(ScanLifecycleResult.RejectionReason.INVALID_STATE)
                session.status == ScanSessionStatus.RUNNING && dao.findActiveLease()?.sessionId != sessionId ->
                    result = ScanLifecycleResult.Rejected(ScanLifecycleResult.RejectionReason.LEASE_NOT_OWNED)
                else -> {
                    check(
                        dao.updateSessionStatus(
                            sessionId,
                            ScanSessionStatus.CANCELLED,
                            updatedAtEpochMs,
                            updatedAtEpochMs,
                        ) == 1,
                    )
                    dao.releaseLease(sessionId)
                    result = ScanLifecycleResult.Applied(sessionId, ScanSessionStatus.CANCELLED)
                }
            }
        }
        return result
    }

    override fun failSession(sessionId: String, updatedAtEpochMs: Long): ScanLifecycleResult {
        lateinit var result: ScanLifecycleResult
        database.runInTransaction {
            val session = dao.findSession(sessionId)
            when {
                session == null -> result = ScanLifecycleResult.Rejected(ScanLifecycleResult.RejectionReason.NOT_FOUND)
                session.status !in ACTIVE_OR_PAUSED_STATUSES ->
                    result = ScanLifecycleResult.Rejected(ScanLifecycleResult.RejectionReason.INVALID_STATE)
                session.status == ScanSessionStatus.RUNNING && dao.findActiveLease()?.sessionId != sessionId ->
                    result = ScanLifecycleResult.Rejected(ScanLifecycleResult.RejectionReason.LEASE_NOT_OWNED)
                else -> {
                    check(
                        dao.updateSessionStatus(
                            sessionId,
                            ScanSessionStatus.FAILED,
                            updatedAtEpochMs,
                            updatedAtEpochMs,
                        ) == 1,
                    )
                    dao.releaseLease(sessionId)
                    result = ScanLifecycleResult.Applied(sessionId, ScanSessionStatus.FAILED)
                }
            }
        }
        return result
    }

    override fun isMissingReconciliationAllowed(
        sessionId: String,
        sourceId: String,
    ): Boolean = dao.isMissingReconciliationAllowed(
        sessionId,
        sourceId,
        ScanSessionStatus.COMPLETED,
        ScanCheckpointCoverage.COMPLETE,
    )

    private companion object {
        val RESUMABLE_STATUSES = setOf(ScanSessionStatus.RUNNING, ScanSessionStatus.PAUSED)
        val ACTIVE_OR_PAUSED_STATUSES = setOf(ScanSessionStatus.RUNNING, ScanSessionStatus.PAUSED)
    }
}
