package com.theemrock.smoothmedia.catalog.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanLeaseRecoveryPolicyTest {
    @Test
    fun heartbeatWithinWindow_isNotStale() {
        assertFalse(
            ScanLeaseRecoveryPolicy.isStale(
                nowEpochMs = 300_999L,
                lastProgressAtEpochMs = 1_000L,
            ),
        )
    }

    @Test
    fun heartbeatOutsideWindow_isStale() {
        assertTrue(
            ScanLeaseRecoveryPolicy.isStale(
                nowEpochMs = 301_001L,
                lastProgressAtEpochMs = 1_000L,
            ),
        )
    }

    @Test
    fun futureHeartbeat_isNotStale() {
        assertFalse(
            ScanLeaseRecoveryPolicy.isStale(
                nowEpochMs = 1_000L,
                lastProgressAtEpochMs = 2_000L,
            ),
        )
    }
}
