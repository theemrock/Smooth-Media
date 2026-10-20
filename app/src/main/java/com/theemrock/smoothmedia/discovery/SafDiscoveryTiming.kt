package com.theemrock.smoothmedia.discovery

/** Session-scoped SAF operation counters; it does not retain document or URI identifiers. */
class SafDiscoveryTimingCollector {
    private var queryCount = 0
    private var queryNanos = 0L
    private var openFileDescriptorCount = 0
    private var openFileDescriptorNanos = 0L
    private var uriCreationCount = 0
    private var uriCreationNanos = 0L

    fun recordQuery(durationNanos: Long) {
        queryCount++
        queryNanos += durationNanos.coerceAtLeast(0L)
    }

    fun recordOpenFileDescriptor(durationNanos: Long) {
        openFileDescriptorCount++
        openFileDescriptorNanos += durationNanos.coerceAtLeast(0L)
    }

    fun recordUriCreation(durationNanos: Long) {
        uriCreationCount++
        uriCreationNanos += durationNanos.coerceAtLeast(0L)
    }

    fun snapshot(totalWallClockNanos: Long): SafDiscoveryTiming {
        val measured = queryNanos + openFileDescriptorNanos + uriCreationNanos
        return SafDiscoveryTiming(
            queryCount = queryCount,
            queryNanos = queryNanos,
            openFileDescriptorCount = openFileDescriptorCount,
            openFileDescriptorNanos = openFileDescriptorNanos,
            uriCreationCount = uriCreationCount,
            uriCreationNanos = uriCreationNanos,
            otherNanos = (totalWallClockNanos - measured).coerceAtLeast(0L),
        )
    }
}

data class SafDiscoveryTiming(
    val queryCount: Int,
    val queryNanos: Long,
    val openFileDescriptorCount: Int,
    val openFileDescriptorNanos: Long,
    val uriCreationCount: Int,
    val uriCreationNanos: Long,
    val otherNanos: Long,
) {
    fun summaryLogLine(totalWallClockNanos: Long): String =
        "SAF_DISCOVERY_DETAIL|queryCount=$queryCount|queryMs=${queryNanos / NANOS_PER_MILLISECOND}|" +
            "openFileDescriptorCount=$openFileDescriptorCount|openFileDescriptorMs=${openFileDescriptorNanos / NANOS_PER_MILLISECOND}|" +
            "uriCreationCount=$uriCreationCount|uriCreationMs=${uriCreationNanos / NANOS_PER_MILLISECOND}|" +
            "otherMs=${otherNanos / NANOS_PER_MILLISECOND}|wallClockMs=${totalWallClockNanos / NANOS_PER_MILLISECOND}"

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
