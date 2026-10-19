package com.theemrock.smoothmedia.catalog.scan

import kotlin.math.ceil

/** Session-scoped, anonymous scan timing collector. It never stores file or metadata identifiers. */
class ScanPerformanceProfiler {
    private val samples = linkedMapOf<ScanPerformanceStage, MutableList<Long>>()

    fun record(stage: ScanPerformanceStage, durationNanos: Long) {
        if (durationNanos >= 0L) samples.getOrPut(stage) { mutableListOf() } += durationNanos
    }

    fun <T> measure(stage: ScanPerformanceStage, block: () -> T): T {
        val startedAt = System.nanoTime()
        return try {
            block()
        } finally {
            record(stage, System.nanoTime() - startedAt)
        }
    }

    fun snapshot(): List<ScanPerformanceMetric> = samples.map { (stage, values) ->
        val sorted = values.sorted()
        ScanPerformanceMetric(
            stage = stage,
            count = sorted.size,
            totalNanos = sorted.sum(),
            averageNanos = sorted.average().toLong(),
            p50Nanos = percentile(sorted, 0.50),
            p95Nanos = percentile(sorted, 0.95),
        )
    }

    fun summaryLogLine(): String = buildString {
        append("SMOOTH_SCAN_PERF|")
        append(snapshot().joinToString(";") { metric ->
            "stage=${metric.stage.name},count=${metric.count},totalMs=${metric.totalNanos / NANOS_PER_MILLISECOND},avgMs=${metric.averageNanos / NANOS_PER_MILLISECOND},p50Ms=${metric.p50Nanos / NANOS_PER_MILLISECOND},p95Ms=${metric.p95Nanos / NANOS_PER_MILLISECOND}"
        })
    }

    private fun percentile(sorted: List<Long>, fraction: Double): Long {
        if (sorted.isEmpty()) return 0L
        val index = (ceil(sorted.size * fraction).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

data class ScanPerformanceMetric(
    val stage: ScanPerformanceStage,
    val count: Int,
    val totalNanos: Long,
    val averageNanos: Long,
    val p50Nanos: Long,
    val p95Nanos: Long,
)

enum class ScanPerformanceStage {
    SAF_DISCOVERY,
    FILE_OPEN,
    FULL_FILE_HASH_INCLUSIVE,
    METADATA_EXTRACTION_INCLUSIVE,
    IDENTITY_CATALOG_INGESTION_INCLUSIVE,
    ROOM_TRANSACTION,
    CHECKPOINT,
    SESSION_CHECKPOINT,
    TOTAL_SCAN,
}
