package com.theemrock.smoothmedia.catalog.scan

/**
 * Decides whether a persisted scan heartbeat is old enough for an explicit user resume.
 * A stale lease is never taken over automatically by starting a new scan.
 */
object ScanLeaseRecoveryPolicy {
    const val DEFAULT_STALE_AFTER_MS: Long = 5 * 60 * 1000L

    fun isStale(
        nowEpochMs: Long,
        lastProgressAtEpochMs: Long,
        staleAfterMs: Long = DEFAULT_STALE_AFTER_MS,
    ): Boolean {
        require(staleAfterMs >= 0) { "staleAfterMs must be non-negative" }
        return nowEpochMs >= lastProgressAtEpochMs &&
            nowEpochMs - lastProgressAtEpochMs >= staleAfterMs
    }
}
