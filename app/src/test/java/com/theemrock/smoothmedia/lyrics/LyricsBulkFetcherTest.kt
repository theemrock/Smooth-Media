package com.theemrock.smoothmedia.lyrics

import com.theemrock.smoothmedia.catalog.database.dao.LyricsCacheDao
import com.theemrock.smoothmedia.catalog.database.dao.LyricsCandidateRow
import com.theemrock.smoothmedia.catalog.database.entity.LyricsCacheEntity
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsBulkFetcherTest {

    private class FakeDao(private val rows: List<LyricsCandidateRow>) : LyricsCacheDao {
        override fun find(trackId: String): LyricsCacheEntity? = null
        override fun upsert(value: LyricsCacheEntity) = Unit
        override fun delete(trackId: String): Int = 0
        override fun listUntouchedTracks(): List<LyricsCandidateRow> = rows
        override fun listTracksByMissKind(kind: String): List<LyricsCandidateRow> = rows
    }

    private fun rows(count: Int) = (1..count).map { LyricsCandidateRow("t$it", "Title $it", "Artist", null, null) }

    private fun noSleep(@Suppress("UNUSED_PARAMETER") ms: Long) = Unit

    @Test
    fun `runs several lookups at once and processes every track`() {
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val fetch = LyricsTrackFetch { _, _, _, _, _ ->
            val now = inFlight.incrementAndGet()
            peak.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            Thread.sleep(20)
            inFlight.decrementAndGet()
            LyricsFetchOutcome.Stored(LyricsMode.SYNCED, "lrclib")
        }
        val fetcher = LyricsBulkFetcher(FakeDao(rows(30)), fetch, workers = 4, sleepMs = ::noSleep)

        val progress = fetcher.run(LyricsBulkMode.NEW, cancelled = { false }, onProgress = {})

        assertEquals(30, progress.processed)
        assertEquals(30, progress.stored)
        assertTrue("expected parallel lookups, peak was ${peak.get()}", peak.get() > 1)
        assertTrue("peak ${peak.get()} above worker count", peak.get() <= 4)
        assertTrue(progress.finished)
    }

    @Test
    fun `stops early after ten failures in a row`() {
        val calls = AtomicInteger(0)
        val fetch = LyricsTrackFetch { _, _, _, _, _ ->
            calls.incrementAndGet()
            LyricsFetchOutcome.Failed("http 503")
        }
        val fetcher = LyricsBulkFetcher(FakeDao(rows(40)), fetch, workers = 1, sleepMs = ::noSleep)

        val progress = fetcher.run(LyricsBulkMode.NEW, cancelled = { false }, onProgress = {})

        assertTrue(progress.stoppedEarly)
        assertEquals(10, progress.processed)
        assertEquals(10, progress.failed)
        assertEquals(10, calls.get())
    }

    @Test
    fun `a failure makes the next lookup wait out the cooldown`() {
        val sleeps = ConcurrentLinkedQueue<Long>()
        var clock = 1_000L
        val outcomes = ArrayDeque(listOf<LyricsFetchOutcome>(LyricsFetchOutcome.Failed("x"), LyricsFetchOutcome.NotFound))
        val fetch = LyricsTrackFetch { _, _, _, _, _ -> outcomes.removeFirst() }
        val fetcher = LyricsBulkFetcher(
            FakeDao(rows(2)),
            fetch,
            workers = 1,
            nowMs = { clock },
            sleepMs = { ms -> sleeps.add(ms); clock += ms },
        )

        fetcher.run(LyricsBulkMode.NEW, cancelled = { false }, onProgress = {})

        assertEquals(listOf(LyricsBulkFetcher.COOLDOWN_AFTER_FAILURE_MS), sleeps.toList())
    }

    @Test
    fun `cancel stops new lookups`() {
        val calls = AtomicInteger(0)
        val fetch = LyricsTrackFetch { _, _, _, _, _ ->
            calls.incrementAndGet()
            LyricsFetchOutcome.NotFound
        }
        val fetcher = LyricsBulkFetcher(FakeDao(rows(50)), fetch, workers = 1, sleepMs = ::noSleep)

        val progress = fetcher.run(LyricsBulkMode.NEW, cancelled = { calls.get() >= 3 }, onProgress = {})

        assertTrue(progress.cancelled)
        assertFalse(progress.stoppedEarly)
        assertEquals(3, calls.get())
    }
}
