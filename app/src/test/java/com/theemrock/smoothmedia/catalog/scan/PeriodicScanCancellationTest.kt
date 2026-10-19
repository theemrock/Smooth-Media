package com.theemrock.smoothmedia.catalog.scan

import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodicScanCancellationTest {
    @Test
    fun firstCheckIsImmediateAndSubsequentChecksAreThrottled() {
        var now = 0L
        var statusQueries = 0
        val cancellation = PeriodicScanCancellation(
            statusProvider = {
                statusQueries++
                ScanSessionStatus.RUNNING
            },
            nowNanos = { now },
        )

        assertFalse(cancellation.isCancelled())
        repeat(8) { assertFalse(cancellation.isCancelled()) }
        assertEquals(1, statusQueries)

        now = 99_000_000L
        assertFalse(cancellation.isCancelled())
        assertEquals(1, statusQueries)

        now = 100_000_000L
        assertFalse(cancellation.isCancelled())
        assertEquals(2, statusQueries)
    }

    @Test
    fun pauseAndCancelAreObservedAtPeriodicRefresh() {
        var now = 0L
        var status = ScanSessionStatus.RUNNING
        val cancellation = PeriodicScanCancellation(
            statusProvider = { status },
            nowNanos = { now },
        )

        assertFalse(cancellation.isCancelled())
        status = ScanSessionStatus.PAUSED
        now = 100_000_000L
        assertTrue(cancellation.isCancelled())

        var cancelStatus = ScanSessionStatus.RUNNING
        val cancel = PeriodicScanCancellation(
            statusProvider = { cancelStatus },
            nowNanos = { now },
        )
        now = 0L
        assertFalse(cancel.isCancelled())
        cancelStatus = ScanSessionStatus.CANCELLED
        now = 100_000_000L
        assertTrue(cancel.isCancelled())
    }

    @Test
    fun nonRunningAndMissingSessionsCancelImmediately() {
        val paused = PeriodicScanCancellation(
            statusProvider = { ScanSessionStatus.PAUSED },
            nowNanos = { 0L },
        )
        val missing = PeriodicScanCancellation(
            statusProvider = { null },
            nowNanos = { 0L },
        )

        assertTrue(paused.isCancelled())
        assertTrue(missing.isCancelled())
    }
}
