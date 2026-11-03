package com.theemrock.smoothmedia.lyrics

import com.theemrock.smoothmedia.catalog.database.dao.LyricsCacheDao
import com.theemrock.smoothmedia.catalog.database.dao.LyricsCandidateRow

/** Which tracks a bulk run covers. */
enum class LyricsBulkMode {
    /** Tracks never looked up before (default). */
    NEW,
    /** Only tracks whose last lookup found no record. */
    NOT_FOUND,
    /** Only tracks whose last lookup failed with a transient error. */
    ERROR,
}

/**
 * Bulk lyrics download started by the user from Settings.
 *
 * Runs one lookup at a time, pauses between real network requests, cools down after a failure,
 * and stops early after repeated failures. Every outcome is recorded, so a normal run only covers
 * tracks that were never looked up; the other modes re-run a chosen group on purpose.
 */
class LyricsBulkFetcher(
    private val dao: LyricsCacheDao,
    private val fetch: LyricsFetchUseCase,
    private val pauseBetweenRequestsMs: Long = PAUSE_MS,
    private val sleepMs: (Long) -> Unit = { Thread.sleep(it) },
) {
    data class Progress(
        val total: Int,
        val processed: Int = 0,
        val stored: Int = 0,
        val notFound: Int = 0,
        val failed: Int = 0,
        val stoppedEarly: Boolean = false,
        val cancelled: Boolean = false,
    ) {
        val finished: Boolean get() = processed >= total || stoppedEarly || cancelled
    }

    /** One line of the per-track log shown on the bulk screen. */
    data class LogEntry(val title: String, val artist: String, val result: String)

    fun run(
        mode: LyricsBulkMode,
        cancelled: () -> Boolean,
        onProgress: (Progress) -> Unit,
        onEntry: (LogEntry) -> Unit = {},
    ): Progress {
        val candidates: List<LyricsCandidateRow> = when (mode) {
            LyricsBulkMode.NEW -> dao.listUntouchedTracks()
            LyricsBulkMode.NOT_FOUND -> dao.listTracksByMissKind(LyricsFetchUseCase.KIND_NOT_FOUND)
            LyricsBulkMode.ERROR -> dao.listTracksByMissKind(LyricsFetchUseCase.KIND_ERROR)
        }
        var progress = Progress(total = candidates.size)
        onProgress(progress)
        var consecutiveFailures = 0

        for (candidate in candidates) {
            if (cancelled()) {
                progress = progress.copy(cancelled = true)
                break
            }
            val outcome = fetch.fetch(
                candidate.trackId,
                candidate.title,
                candidate.artist,
                candidate.album,
                candidate.durationMs,
            )
            onEntry(
                LogEntry(
                    title = candidate.title,
                    artist = candidate.artist,
                    result = when (outcome) {
                        is LyricsFetchOutcome.Stored ->
                            "indirildi (${if (outcome.mode == LyricsMode.SYNCED) "senkron" else "düz metin"})"
                        is LyricsFetchOutcome.AlreadyCached -> "zaten kayıtlı"
                        LyricsFetchOutcome.NotFound -> "bulunamadı"
                        LyricsFetchOutcome.Instrumental -> "enstrümantal"
                        is LyricsFetchOutcome.Failed -> "hata: ${outcome.reason}"
                    },
                ),
            )
            progress = when (outcome) {
                is LyricsFetchOutcome.Stored -> progress.copy(processed = progress.processed + 1, stored = progress.stored + 1)
                is LyricsFetchOutcome.AlreadyCached -> progress.copy(processed = progress.processed + 1)
                LyricsFetchOutcome.NotFound, LyricsFetchOutcome.Instrumental ->
                    progress.copy(processed = progress.processed + 1, notFound = progress.notFound + 1)
                is LyricsFetchOutcome.Failed -> progress.copy(processed = progress.processed + 1, failed = progress.failed + 1)
            }
            onProgress(progress)

            consecutiveFailures = if (outcome is LyricsFetchOutcome.Failed) consecutiveFailures + 1 else 0
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                progress = progress.copy(stoppedEarly = true)
                onProgress(progress)
                break
            }
            // Cool down after a failure so a struggling service is not hit again immediately.
            // Politeness pause after a real network request otherwise; none for cache hits.
            when (outcome) {
                is LyricsFetchOutcome.Failed -> sleepMs(COOLDOWN_AFTER_FAILURE_MS)
                is LyricsFetchOutcome.AlreadyCached -> Unit
                else -> sleepMs(pauseBetweenRequestsMs)
            }
        }
        return progress
    }

    companion object {
        const val PAUSE_MS = 250L
        const val MAX_CONSECUTIVE_FAILURES = 10
        const val COOLDOWN_AFTER_FAILURE_MS = 30_000L
    }
}
