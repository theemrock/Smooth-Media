package com.theemrock.smoothmedia.discovery

import org.junit.Assert.assertEquals
import org.junit.Test

class SafDiscoveryTimingTest {
    @Test
    fun snapshotReportsOperationCountsAndUnmeasuredRemainder() {
        val timing = SafDiscoveryTimingCollector()
        timing.recordQuery(10)
        timing.recordQuery(20)
        timing.recordOpenFileDescriptor(7)
        timing.recordUriCreation(3)

        assertEquals(
            SafDiscoveryTiming(
                queryCount = 2,
                queryNanos = 30,
                openFileDescriptorCount = 1,
                openFileDescriptorNanos = 7,
                uriCreationCount = 1,
                uriCreationNanos = 3,
                otherNanos = 60,
            ),
            timing.snapshot(totalWallClockNanos = 100),
        )
    }

    @Test
    fun snapshotNeverReportsNegativeOtherTime() {
        val timing = SafDiscoveryTimingCollector()
        timing.recordQuery(100)
        timing.recordOpenFileDescriptor(100)
        timing.recordUriCreation(100)

        assertEquals(0, timing.snapshot(totalWallClockNanos = 1).otherNanos)
    }

    @Test
    fun summaryContainsCountsAndWallClockWithoutIdentifiers() {
        val timing = SafDiscoveryTimingCollector()
        timing.recordQuery(10)

        val line = timing.snapshot(20).summaryLogLine(20)

        assertEquals(
            "SAF_DISCOVERY_DETAIL|queryCount=1|queryMs=0|openFileDescriptorCount=0|" +
                "openFileDescriptorMs=0|uriCreationCount=0|uriCreationMs=0|otherMs=0|wallClockMs=0",
            line,
        )
    }
}
