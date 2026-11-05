package com.theemrock.smoothmedia.lyrics

import com.theemrock.smoothmedia.catalog.database.dao.LyricsCacheDao
import com.theemrock.smoothmedia.catalog.database.dao.LyricsCandidateRow
import java.util.concurrent.Executors

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
 * Runs [workers] lookups in parallel. Real HTTP spacing is enforced by the shared request gate in
 * [LrclibClient], so parallel workers never burst the service. A failure sets a shared cooldown that
 * pauses every worker, and [MAX_CONSECUTIVE_FAILURES] failures in a row stop the run early. Every
 * outcome is recorded, so a normal run only covers tracks that were never looked up; the other modes
 * re-run a chosen group on purpose.
 */
class LyricsBulkFetcher(
    private val dao: LyricsCacheDao,
    private val fetch: LyricsTrackFetch,
    private val workers: Int = WORKERS,
    private val nowMs: () -> Long = System::currentTimeMillis,
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
        val state = Run(candidates, cancelled, onProgress, onEntry)
        onProgress(state.snapshot())

        val pool = Executors.newFixedThreadPool(workers.coerceAtLeast(1))
        try {
            val futures = List(workers.coerceAtLeast(1)) { pool.submit(Runnable { state.worker() }) }
            futures.forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }
        return state.snapshot()
    }

    /** Shared state of one bulk run. All mutable fields are guarded by [lock]. */
    private inner class Run(
        candidates: List<LyricsCandidateRow>,
        private val cancelled: () -> Boolean,
        private val onProgress: (Progress) -> Unit,
        private val onEntry: (LogEntry) -> Unit,
    ) {
        private val lock = Any()
        private val queue = ArrayDeque(candidates)
        private var progress = Progress(total = candidates.size)
        private var consecutiveFailures = 0
        private var stopped = false
        @Volatile private var cooldownUntilMs = 0L

        fun snapshot(): Progress = synchronized(lock) { progress }

        fun worker() {
            while (true) {
                if (cancelled()) {
                    stop { it.copy(cancelled = true) }
                    return
                }
                val candidate = synchronized(lock) {
                    if (stopped || queue.isEmpty()) null else queue.removeFirst()
                } ?: return
                awaitCooldown()
                val outcome = fetch.fetch(
                    candidate.trackId,
                    candidate.title,
                    candidate.artist,
                    candidate.album,
                    candidate.durationMs,
                )
                if (outcome is LyricsFetchOutcome.Failed) {
                    cooldownUntilMs = nowMs() + COOLDOWN_AFTER_FAILURE_MS
                }
                record(candidate, outcome)
            }
        }

        private fun awaitCooldown() {
            val wait = cooldownUntilMs - nowMs()
            if (wait > 0) sleepMs(wait)
        }

        private fun record(candidate: LyricsCandidateRow, outcome: LyricsFetchOutcome) = synchronized(lock) {
            if (stopped) return@synchronized
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
            consecutiveFailures = if (outcome is LyricsFetchOutcome.Failed) consecutiveFailures + 1 else 0
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                stop { it.copy(processed = it.processed + 1, failed = it.failed + 1, stoppedEarly = true) }
                return@synchronized
            }
            update(outcome)
        }

        private fun update(outcome: LyricsFetchOutcome) {
            progress = when (outcome) {
                is LyricsFetchOutcome.Stored -> progress.copy(processed = progress.processed + 1, stored = progress.stored + 1)
                is LyricsFetchOutcome.AlreadyCached -> progress.copy(processed = progress.processed + 1)
                LyricsFetchOutcome.NotFound, LyricsFetchOutcome.Instrumental ->
                    progress.copy(processed = progress.processed + 1, notFound = progress.notFound + 1)
                is LyricsFetchOutcome.Failed -> progress.copy(processed = progress.processed + 1, failed = progress.failed + 1)
            }
            onProgress(progress)
        }

        /** Ends the run: applies [change] once, reports it, and tells every worker to stop. */
        private fun stop(change: (Progress) -> Progress) = synchronized(lock) {
            if (stopped) return@synchronized
            stopped = true
            progress = change(progress)
            onProgress(progress)
        }
    }

    companion object {
        /** Parallel lookups. Each lookup can take 1 to 7 s, so a few workers are needed to fill the request gap. */
        const val WORKERS = 6
        const val MAX_CONSECUTIVE_FAILURES = 10
        const val COOLDOWN_AFTER_FAILURE_MS = 3_000L
    }
}
