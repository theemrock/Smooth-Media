package com.theemrock.smoothmedia.metadata

import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.MetadataApplyItemEntity
import com.theemrock.smoothmedia.catalog.database.entity.MetadataApplySessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.MetadataRecoverySessionStatus
import java.util.UUID

/** Persists dry-run apply batches and their lease without touching track_metadata. */
class RoomMetadataApplySessionStore(
    private val database: CatalogDatabase,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    fun prepareDryRun(auditSessionId: String, decisions: List<ApplyDecisionInput>): String {
        check(database.metadataRecoveryDao().findSession(auditSessionId)?.status == MetadataRecoverySessionStatus.COMPLETED) {
            "Apply yalnızca tamamlanmış audit session'ından hazırlanabilir"
        }
        val accepted = decisions.filter { it.decision == MetadataApplyCoordinator.AUTO_REPAIR }
        database.metadataApplyDao().findPendingDryRun(auditSessionId)?.let { return it.sessionId }
        val sessionId = "metadata-apply-${UUID.randomUUID()}"
        val now = nowEpochMs()
        database.runInTransaction {
            database.metadataApplyDao().insertSession(
                MetadataApplySessionEntity(
                    sessionId = sessionId,
                    auditSessionId = auditSessionId,
                    status = "DRY_RUN",
                    cursorDecisionKey = null,
                    totalItems = accepted.size,
                    processedItems = 0,
                    successCount = 0,
                    skippedStaleCount = 0,
                    failedCount = 0,
                    startedAtEpochMs = now,
                    updatedAtEpochMs = now,
                    completedAtEpochMs = null,
                ),
            )
            database.metadataApplyDao().insertItems(accepted.map {
                MetadataApplyItemEntity(
                    applySessionId = sessionId,
                    decisionSessionId = auditSessionId,
                    trackId = it.trackId,
                    fieldName = it.field.name,
                    sourceId = it.sourceId,
                    locationId = it.locationId,
                    expectedOldValue = it.expectedOldValue,
                    newValue = it.newValue,
                    evidenceType = it.evidence,
                    state = "PENDING",
                    errorMessage = null,
                    updatedAtEpochMs = now,
                )
            })
        }
        return sessionId
    }

    fun acquireLease(sessionId: String, owner: String, leaseDurationMs: Long): Boolean {
        val now = nowEpochMs()
        return database.metadataApplyDao().acquireOrRenewLease(sessionId, owner, now, now + leaseDurationMs) == 1
    }

    fun releaseLease(sessionId: String, owner: String): Boolean =
        database.metadataApplyDao().releaseLease(sessionId, owner) == 1
}
