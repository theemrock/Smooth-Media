package com.theemrock.smoothmedia.metadata

enum class MetadataAutonomousApplyDecision {
    DISABLED,
    BLOCKED_AUDIT,
    BLOCKED_BACKUP,
    NO_WORK,
    READY,
}

object MetadataAutonomousApplyPolicy {
    fun evaluate(
        enabled: Boolean,
        auditCompleted: Boolean,
        backupVerified: Boolean,
        eligible: Int,
        alreadyCompleted: Boolean,
    ): MetadataAutonomousApplyDecision = when {
        !enabled -> MetadataAutonomousApplyDecision.DISABLED
        !auditCompleted -> MetadataAutonomousApplyDecision.BLOCKED_AUDIT
        !backupVerified -> MetadataAutonomousApplyDecision.BLOCKED_BACKUP
        alreadyCompleted || eligible <= 0 -> MetadataAutonomousApplyDecision.NO_WORK
        else -> MetadataAutonomousApplyDecision.READY
    }
}
