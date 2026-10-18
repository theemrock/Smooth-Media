package com.theemrock.smoothmedia.metadata

import com.theemrock.smoothmedia.catalog.database.CatalogDatabase

data class MetadataAutonomousApplyResult(
    val decision: MetadataAutonomousApplyDecision,
    val status: MetadataApplyExecutionStatus? = null,
    val processed: Int = 0,
    val succeeded: Int = 0,
    val message: String? = null,
)

/**
 * Audit-completion trigger for the opt-in autonomous path. The caller must
 * provide a fresh, independently verified backup proof; a boolean default is
 * intentionally false so this path cannot write by accident.
 */
class MetadataAutonomousApplyCoordinator(
    private val database: CatalogDatabase,
    private val settings: MetadataAutonomousSettings,
    private val owner: String,
    private val backupVerified: () -> Boolean,
) {
    fun onAuditCompleted(auditSessionId: String): MetadataAutonomousApplyResult {
        val dryRun = RoomMetadataApplyDryRun(database)
        val audit = database.metadataRecoveryDao().findSession(auditSessionId)
        val summary = runCatching { dryRun.evaluate(auditSessionId) }.getOrElse {
            return MetadataAutonomousApplyResult(
                decision = MetadataAutonomousApplyDecision.BLOCKED_AUDIT,
                message = it.message,
            )
        }
        val alreadyCompleted = database.metadataApplyDao().findCompletedForAudit(auditSessionId) != null
        val decision = MetadataAutonomousApplyPolicy.evaluate(
            enabled = settings.enabled,
            auditCompleted = audit?.status?.name == "COMPLETED",
            backupVerified = backupVerified(),
            eligible = summary.eligible,
            alreadyCompleted = alreadyCompleted,
        )
        if (decision != MetadataAutonomousApplyDecision.READY) {
            return MetadataAutonomousApplyResult(decision, message = decision.name)
        }

        val inputs = dryRun.approvedInputs(auditSessionId)
        val sessionId = RoomMetadataApplySessionStore(database).prepareDryRun(auditSessionId, inputs)
        val executor = RoomMetadataApplyExecutor(
            database = database,
            writer = ControlledMetadataRoomWriter(database),
            owner = owner,
        )
        var result = executor.runNextBatch(sessionId, applyEnabled = true, backupVerified = true)
        while (result.status == MetadataApplyExecutionStatus.RUNNING) {
            result = executor.runNextBatch(sessionId, applyEnabled = true, backupVerified = true)
        }
        return MetadataAutonomousApplyResult(
            decision = decision,
            status = result.status,
            processed = result.processed,
            succeeded = result.succeeded,
        )
    }
}
