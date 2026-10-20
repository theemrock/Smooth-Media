package com.theemrock.smoothmedia.catalog.scan

import kotlin.math.ceil

/** Session-scoped, anonymous scan timing collector. It never stores file or metadata identifiers. */
class ScanPerformanceProfiler {
    private val lock = Any()
    private val samples = linkedMapOf<ScanPerformanceStage, MutableList<Long>>()
    private val statuses = linkedMapOf<ScanPerformanceStage, ScanPerformanceStatus>()

    fun record(stage: ScanPerformanceStage, durationNanos: Long) {
        synchronized(lock) {
            if (durationNanos >= 0L) {
                samples.getOrPut(stage) { mutableListOf() } += durationNanos
                statuses[stage] = ScanPerformanceStatus.MEASURED
            }
        }
    }

    fun markSkipped(stage: ScanPerformanceStage) {
        synchronized(lock) {
            if (statuses[stage] != ScanPerformanceStatus.MEASURED) {
                statuses[stage] = ScanPerformanceStatus.SKIPPED
            }
        }
    }

    fun <T> measure(stage: ScanPerformanceStage, block: () -> T): T {
        val startedAt = System.nanoTime()
        return try {
            block()
        } finally {
            record(stage, System.nanoTime() - startedAt)
        }
    }

    fun snapshot(): List<ScanPerformanceMetric> = synchronized(lock) {
        samples.map { (stage, values) ->
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
    }

    fun summaryLogLine(): String = buildString {
        append("SMOOTH_SCAN_PERF|")
        append(ScanPerformanceStage.entries.joinToString(";") { stage ->
            val metric = snapshot().firstOrNull { it.stage == stage }
            val status = synchronized(lock) { statuses[stage] ?: ScanPerformanceStatus.NOT_EXECUTED }
            "stage=${stage.name},status=${status.name},count=${metric?.count ?: 0},totalMs=${metric?.totalNanos?.div(NANOS_PER_MILLISECOND) ?: 0},avgMs=${metric?.averageNanos?.div(NANOS_PER_MILLISECOND) ?: 0},p50Ms=${metric?.p50Nanos?.div(NANOS_PER_MILLISECOND) ?: 0},p95Ms=${metric?.p95Nanos?.div(NANOS_PER_MILLISECOND) ?: 0}"
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
    FULL_FILE_HASH_WALL_CLOCK,
    METADATA_EXTRACTION_INCLUSIVE,
    IDENTITY_CATALOG_INGESTION_INCLUSIVE,
    ROOM_TRANSACTION,
    CHECKPOINT,
    SESSION_CHECKPOINT,
    TOTAL_SCAN,
}

enum class ScanPerformanceStatus {
    MEASURED,
    SKIPPED,
    NOT_EXECUTED,
    UNKNOWN,
}
