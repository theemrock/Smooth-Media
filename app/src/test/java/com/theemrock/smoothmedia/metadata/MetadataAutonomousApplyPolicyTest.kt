package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataAutonomousApplyPolicyTest {
    @Test
    fun disabledByDefault_neverStartsApply() {
        val result = MetadataAutonomousApplyPolicy.evaluate(
            enabled = false,
            auditCompleted = true,
            backupVerified = true,
            eligible = 59,
            alreadyCompleted = false,
        )

        assertEquals(MetadataAutonomousApplyDecision.DISABLED, result)
    }

    @Test
    fun enabledWithoutVerifiedBackup_isBlocked() {
        val result = MetadataAutonomousApplyPolicy.evaluate(
            enabled = true,
            auditCompleted = true,
            backupVerified = false,
            eligible = 59,
            alreadyCompleted = false,
        )

        assertEquals(MetadataAutonomousApplyDecision.BLOCKED_BACKUP, result)
    }

    @Test
    fun enabledWithCompletedAuditAndBackup_canStartOnlyWhenEligible() {
        assertEquals(
            MetadataAutonomousApplyDecision.READY,
            MetadataAutonomousApplyPolicy.evaluate(true, true, true, 59, false),
        )
        assertEquals(
            MetadataAutonomousApplyDecision.NO_WORK,
            MetadataAutonomousApplyPolicy.evaluate(true, true, true, 0, false),
        )
        assertEquals(
            MetadataAutonomousApplyDecision.NO_WORK,
            MetadataAutonomousApplyPolicy.evaluate(true, true, true, 59, true),
        )
    }

    @Test
    fun incompleteAudit_isBlockedBeforeAnyApply() {
        val result = MetadataAutonomousApplyPolicy.evaluate(true, false, true, 59, false)

        assertEquals(MetadataAutonomousApplyDecision.BLOCKED_AUDIT, result)
    }
}
