package com.theemrock.smoothmedia.catalog.scan

import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualScanControllerTest {
    @Test
    fun resultStatus_preservesLifecycleOutcome() {
        assertEquals(
            ScanSessionStatus.COMPLETED,
            ManualScanResult.Completed(
                sessionId = "session",
                skippedUnreadableCount = 0,
                processedCount = 7066,
                successfulCount = 7066,
            ).status(),
        )
        assertEquals(
            ScanSessionStatus.PAUSED,
            ManualScanResult.Paused("session").status(),
        )
        assertEquals(
            ScanSessionStatus.CANCELLED,
            ManualScanResult.Cancelled("session").status(),
        )
    }

    @Test
    fun rejectedAndFailureOutcomes_areNotReportedAsCompleted() {
        assertEquals(
            ScanSessionStatus.FAILED,
            ManualScanResult.DiscoveryFailed("session").status(),
        )
        assertEquals(
            ScanSessionStatus.FAILED,
            ManualScanResult.AlreadyActive("session").status(),
        )
    }
}
