package com.theemrock.smoothmedia.catalog.scan

import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualScanResultInstrumentationTest {
    @Test
    fun completedResult_isReportedAsCompleted() {
        assertEquals(
            ScanSessionStatus.COMPLETED,
            ManualScanResult.Completed("isolated", skippedUnreadableCount = 0).status(),
        )
    }

    @Test
    fun failureAndAlreadyActive_areNotReportedAsSuccess() {
        assertEquals(
            ScanSessionStatus.FAILED,
            ManualScanResult.AlreadyActive("isolated").status(),
        )
        assertEquals(
            ScanSessionStatus.FAILED,
            ManualScanResult.DiscoveryFailed("isolated").status(),
        )
    }
}
