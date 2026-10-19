package com.theemrock.smoothmedia.catalog.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPerformanceProfilerTest {
    @Test
    fun recordsAggregateAndPercentileMetricsPerStage() {
        val profiler = ScanPerformanceProfiler()

        profiler.record(ScanPerformanceStage.FULL_FILE_HASH_INCLUSIVE, 1_000_000L)
        profiler.record(ScanPerformanceStage.FULL_FILE_HASH_INCLUSIVE, 2_000_000L)
        profiler.record(ScanPerformanceStage.FULL_FILE_HASH_INCLUSIVE, 3_000_000L)
        profiler.record(ScanPerformanceStage.FULL_FILE_HASH_INCLUSIVE, 4_000_000L)
        profiler.record(ScanPerformanceStage.FULL_FILE_HASH_INCLUSIVE, 5_000_000L)

        val metric = profiler.snapshot().single { it.stage == ScanPerformanceStage.FULL_FILE_HASH_INCLUSIVE }

        assertEquals(5, metric.count)
        assertEquals(15_000_000L, metric.totalNanos)
        assertEquals(3_000_000L, metric.averageNanos)
        assertEquals(3_000_000L, metric.p50Nanos)
        assertEquals(5_000_000L, metric.p95Nanos)
    }

    @Test
    fun summaryLog_isSingleAnonymousLine() {
        val profiler = ScanPerformanceProfiler()
        profiler.record(ScanPerformanceStage.SAF_DISCOVERY, 2_000_000L)

        val line = profiler.summaryLogLine()

        assertTrue(line.startsWith("SMOOTH_SCAN_PERF|"))
        assertTrue(line.contains("stage=SAF_DISCOVERY,status=MEASURED,count=1,totalMs=2"))
        assertFalse(line.contains("content://"))
        assertFalse(line.contains(".mp3"))
    }

    @Test
    fun summaryLog_reportsStagesThatWereNotExecuted() {
        val line = ScanPerformanceProfiler().summaryLogLine()

        assertTrue(line.contains("stage=FULL_FILE_HASH_INCLUSIVE,status=NOT_EXECUTED"))
        assertTrue(line.contains("stage=METADATA_EXTRACTION_INCLUSIVE,status=NOT_EXECUTED"))
    }

    @Test
    fun summaryLog_reportsExplicitlySkippedStages() {
        val profiler = ScanPerformanceProfiler()
        profiler.markSkipped(ScanPerformanceStage.METADATA_EXTRACTION_INCLUSIVE)

        assertTrue(
            profiler.summaryLogLine().contains(
                "stage=METADATA_EXTRACTION_INCLUSIVE,status=SKIPPED",
            ),
        )
    }
}
