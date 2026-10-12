package com.theemrock.smoothmedia.catalog.repository

import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanCheckpointCoverage
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.catalog.database.entity.SourceScanCheckpointEntity

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
    ): Boolean = dao.updateCheckpoint(
        sessionId,
        sourceId,
        continuationToken,
        discoveredCount,
        processedCount,
        coverage,
        updatedAtEpochMs,
    ) == 1

    override fun findCheckpoint(
        sessionId: String,
        sourceId: String,
    ): SourceScanCheckpointEntity? = dao.findCheckpoint(sessionId, sourceId)

    override fun recordCandidate(candidate: ScanCandidateEntity) = dao.insertCandidate(candidate)

    override fun findCandidate(
        sessionId: String,
        sourceId: String,
        candidateKey: String,
    ): ScanCandidateEntity? = dao.findCandidate(sessionId, sourceId, candidateKey)

    override fun completeSession(sessionId: String, completedAtEpochMs: Long) {
        database.runInTransaction {
            check(
                dao.updateSessionStatus(
                    sessionId,
                    ScanSessionStatus.COMPLETED,
                    completedAtEpochMs,
                    completedAtEpochMs,
                ) == 1,
            )
            dao.releaseLease(sessionId)
        }
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
    }
}
