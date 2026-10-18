package com.theemrock.smoothmedia.metadata

import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.MetadataRecoveryDecisionEntity
import com.theemrock.smoothmedia.catalog.database.entity.MetadataRecoverySessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.MetadataRecoverySessionStatus
import java.util.UUID

/** Persists read-only recovery decisions in bounded, resumable Room batches. */
class MetadataRecoverySessionCoordinator(
    private val database: CatalogDatabase,
    private val engine: MetadataRecoveryEngine,
    private val owner: String,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val leaseDurationMs: Long = 60_000L,
) {
    fun start(): String {
        database.metadataRecoveryDao().findResumableSession()?.let { return it.sessionId }
        val sessionId = "metadata-recovery-${UUID.randomUUID()}"
        val now = nowEpochMs()
        database.metadataRecoveryDao().insertSession(
            MetadataRecoverySessionEntity(
                sessionId = sessionId,
                status = MetadataRecoverySessionStatus.RUNNING,
                cursorTrackId = null,
                totalTracks = database.trackDao().countActiveTracks(),
                processedTracks = 0,
                autoRepairCount = 0,
                noChangeCount = 0,
                reviewRequiredCount = 0,
                unrecoverableCount = 0,
                readErrorCount = 0,
                leaseOwner = null,
                leaseExpiresAtEpochMs = null,
                startedAtEpochMs = now,
                updatedAtEpochMs = now,
                completedAtEpochMs = null,
            ),
        )
        return sessionId
    }

    fun runNextBatch(sessionId: String): MetadataRecoverySessionStatus? {
        val dao = database.metadataRecoveryDao()
        val session = dao.findSession(sessionId) ?: return null
        if (session.status != MetadataRecoverySessionStatus.RUNNING) return session.status
        val now = nowEpochMs()
        if (dao.acquireOrRenewLease(sessionId, owner, now, now + leaseDurationMs) != 1) return session.status

        val ids = database.trackDao().listActiveTrackIdsAfter(session.cursorTrackId, MetadataRecoveryEngine.MAX_BATCH_SIZE)
        if (ids.isEmpty()) {
            dao.commitBatch(sessionId, owner, MetadataRecoverySessionStatus.COMPLETED, session.cursorTrackId, 0, 0, 0, 0, 0, 0, now, now)
            return MetadataRecoverySessionStatus.COMPLETED
        }
        val result = engine.evaluate(ids, limit = MetadataRecoveryEngine.MAX_BATCH_SIZE)
        val decisions = result.tracks.flatMap { track ->
            track.fields.map { field ->
                MetadataRecoveryDecisionEntity(
                    sessionId = sessionId,
                    trackId = track.trackId,
                    fieldName = field.field.name,
                    decision = field.decision.name,
                    evidenceType = field.reason,
                    oldValue = field.current,
                    newValue = field.proposed,
                    createdAtEpochMs = now,
                )
            }
        }
        val counts = result.tracks.flatMap { it.fields }.groupingBy { it.decision }.eachCount()
        val nextStatus = if (result.tracks.size < ids.size) MetadataRecoverySessionStatus.COMPLETED else MetadataRecoverySessionStatus.RUNNING
        database.runInTransaction {
            dao.insertDecisions(decisions)
            check(
                dao.commitBatch(
                    sessionId = sessionId,
                    owner = owner,
                    status = nextStatus,
                    cursorTrackId = result.lastProcessedTrackId,
                    processedTracks = result.tracks.size,
                    autoRepairCount = counts[RecoveryDecision.AUTO_REPAIR] ?: 0,
                    noChangeCount = counts[RecoveryDecision.NO_CHANGE] ?: 0,
                    reviewRequiredCount = counts[RecoveryDecision.REVIEW_REQUIRED] ?: 0,
                    unrecoverableCount = counts[RecoveryDecision.UNRECOVERABLE] ?: 0,
                    readErrorCount = counts[RecoveryDecision.READ_ERROR] ?: 0,
                    nowEpochMs = now,
                    completedAtEpochMs = if (nextStatus == MetadataRecoverySessionStatus.COMPLETED) now else null,
                ) == 1,
            )
        }
        return nextStatus
    }

    fun pause(sessionId: String): Boolean = database.metadataRecoveryDao().pauseSession(
        sessionId = sessionId,
        owner = owner,
        nowEpochMs = nowEpochMs(),
    ) == 1

    fun resume(sessionId: String): Boolean = database.metadataRecoveryDao().resumeSession(
        sessionId = sessionId,
        nowEpochMs = nowEpochMs(),
    ) == 1

    fun cancel(sessionId: String): Boolean = database.metadataRecoveryDao().cancelSession(
        sessionId = sessionId,
        owner = owner,
        nowEpochMs = nowEpochMs(),
    ) == 1

    fun fail(sessionId: String): Boolean = database.metadataRecoveryDao().failSession(
        sessionId = sessionId,
        owner = owner,
        nowEpochMs = nowEpochMs(),
    ) == 1
}
