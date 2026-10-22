package com.theemrock.smoothmedia.p09test

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.theemrock.smoothmedia.playback.CatalogPlaybackLocationResolver
import com.theemrock.smoothmedia.playback.PlaybackLocation
import com.theemrock.smoothmedia.playback.PlaybackWindowResolver
import com.theemrock.smoothmedia.playback.QueueEntryOrigin
import com.theemrock.smoothmedia.playback.QueueItem
import com.theemrock.smoothmedia.playback.PlaybackService
import com.theemrock.smoothmedia.playback.PlaybackQueueController
import com.theemrock.smoothmedia.playback.QueueMode
import com.theemrock.smoothmedia.playback.RepeatMode
import com.theemrock.smoothmedia.source.SourceRegistryFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-ready verification for the isolated p09test application.
 *
 * This test deliberately talks to the existing Media3 service through a
 * MediaController. It does not add hooks to MainActivity or production code.
 */
@RunWith(AndroidJUnit4::class)
class DeterministicPlaybackVerificationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var controller: MediaController? = null
    private var runtime: com.theemrock.smoothmedia.source.SourceRegistryRuntime? = null

    @Before
    fun setUp() {
        assumeTrue("Bu test yalnız p09test hedefinde çalışır", context.packageName.endsWith(".p09test"))
        runtime = SourceRegistryFactory.create(context)
        assumeTrue("p09test katalog verisi yok", runtime!!.database.trackDao().countActiveTracks() > 0)
    }

    @After
    fun tearDown() {
        controller?.let { mediaController ->
            runCatching {
                onMain {
                    mediaController.pause()
                    mediaController.clearMediaItems()
                    mediaController.release()
                }
            }
        }
        controller = null
        runtime?.close()
        runtime = null
    }

    @Test
    fun domainQueueKeepsFullCatalogAndResolvesOnlySelectedWindow() {
        val tracks = loadDomainQueue()
        assertEquals(EXPECTED_TRACK_COUNT, tracks.size)

        val selected = tracks[EXPECTED_SELECTED_INDEX]
        var resolverCalls = 0
        val resolution = PlaybackWindowResolver.resolve(tracks, selected.entryId) { item ->
            resolverCalls++
            CatalogPlaybackLocationResolver(runtime!!.database).resolve(item.trackId).firstOrNull()
        }

        requireNotNull(resolution)
        assertEquals(4, resolution.items.size)
        assertEquals(4, resolverCalls)
        assertEquals(selected.entryId, resolution.items[resolution.startIndex].entryId)
    }

    @Test
    fun firstSelectionAttemptOne_recordsCallbacksAndPollingSeparately() =
        runFirstSelectionAttempt("attempt-1")

    @Test
    fun firstSelectionAttemptTwo_recordsCallbacksAndPollingSeparately() =
        runFirstSelectionAttempt("attempt-2")

    @Test
    fun firstSelectionAttemptThree_recordsCallbacksAndPollingSeparately() =
        runFirstSelectionAttempt("attempt-3")

    @Test
    fun naturalWindowTransitionKeepsPlayingAndMovesToFollowingMetadata() {
        val session = startPlayableSession()
        val next = session.playableItems.getOrNull(session.selectedPlayableIndex + 1)
        assumeTrue("Selected bounded window has no following playable item", next != null)
        val nextItem = requireNotNull(next)

        onMain { session.controller.seekTo((session.controller.duration - 250L).coerceAtLeast(0L)) }
        assertTrue("Natural transition did not reach the following item", waitForMediaId(session.controller, nextItem.first.entryId, 20_000L))
        assertTrue("Natural transition stopped playback", onMain { session.controller.isPlaying })
        assertTrue("Following item position did not advance", waitForAdvancingPosition(session.controller, session.trace))

        val metadata = onMain { session.controller.mediaMetadata }
        assertEquals(nextItem.first.title, metadata.title?.toString())
        assertEquals(nextItem.first.artist, metadata.artist?.toString())
        assertEquals(nextItem.first.album, metadata.albumTitle?.toString())
        logRegression("natural-transition", session, nextItem.first.entryId)
    }

    @Test
    fun nextAndPreviousStayWithinBoundedWindowAndKeepPlaying() {
        val session = startPlayableSession()
        val current = session.playableItems[session.selectedPlayableIndex].first
        val next = session.playableItems.getOrNull(session.selectedPlayableIndex + 1)
        assumeTrue("Selected bounded window has no following playable item", next != null)
        val nextItem = requireNotNull(next)

        onMain { session.controller.seekToNextMediaItem() }
        assertTrue("Next did not reach the following item", waitForMediaId(session.controller, nextItem.first.entryId, 10_000L))
        assertTrue("Next did not resume PLAYING after transition", waitForPlaying(session.controller, 5_000L))
        assertTrue("Next position did not advance", waitForAdvancingPosition(session.controller, session.trace))
        assertEquals(nextItem.first.title, onMain { session.controller.mediaMetadata.title?.toString() })

        onMain { session.controller.seekToPreviousMediaItem() }
        assertTrue("Previous did not return to the selected item", waitForMediaId(session.controller, current.entryId, 10_000L))
        assertTrue("Previous did not resume PLAYING after transition", waitForPlaying(session.controller, 5_000L))
        assertTrue("Previous position did not advance", waitForAdvancingPosition(session.controller, session.trace))
        assertEquals(current.title, onMain { session.controller.mediaMetadata.title?.toString() })
        logRegression("next-previous", session, current.entryId)
    }

    @Test
    fun domainShuffleAndRepeatUseFullGhostQueueOnDevice() {
        val tracks = loadDomainQueue()
        val queue = PlaybackQueueController()
        queue.startGhost(tracks, EXPECTED_SELECTED_INDEX, sourceKey = "p09-regression")
        val selectedEntryId = requireNotNull(queue.state.value.current).entryId

        queue.setShuffle(true)
        assertEquals(tracks.size, queue.state.value.items.size)
        assertEquals(selectedEntryId, queue.orderedForPlayback().first().entryId)
        queue.next()
        assertNotNull(queue.state.value.current)
        assertNotEquals(selectedEntryId, queue.state.value.current?.entryId)

        queue.startGhost(tracks, EXPECTED_SELECTED_INDEX, sourceKey = "p09-regression")
        queue.setRepeat(RepeatMode.ALL)
        val lastIndex = queue.state.value.items.lastIndex
        queue.select(lastIndex)
        queue.next()
        assertEquals(0, queue.state.value.currentIndex)

        queue.setRepeat(RepeatMode.ONE)
        val beforeRepeat = queue.state.value.currentIndex
        queue.next()
        assertEquals(beforeRepeat, queue.state.value.currentIndex)
        android.util.Log.i(TAG, "REGRESSION|scenario=shuffle-repeat|domainQueue=${tracks.size}|mode=${queue.state.value.mode}|PASS")
    }

    @Test
    fun userQueueTakeoverDropsUnplayedGhostEntriesWithoutShrinkingCatalogBeforeTakeover() {
        val tracks = loadDomainQueue()
        val queue = PlaybackQueueController()
        queue.startGhost(tracks, EXPECTED_SELECTED_INDEX, sourceKey = "p09-regression")
        val current = requireNotNull(queue.state.value.current)
        val manual = QueueItem(
            trackId = tracks.first().trackId,
            entryId = "manual-p09-regression",
            title = tracks.first().title,
            artist = tracks.first().artist,
            album = tracks.first().album,
            durationMs = tracks.first().durationMs,
        )

        assertEquals(tracks.size, queue.state.value.items.size)
        assertTrue(queue.takeOverUserQueue(manual, playNext = false))
        assertEquals(QueueMode.USER, queue.state.value.mode)
        assertEquals(listOf(current.entryId, manual.entryId), queue.state.value.items.map { it.entryId })
        assertEquals(0, queue.state.value.currentIndex)
        android.util.Log.i(TAG, "REGRESSION|scenario=user-queue-takeover|domainQueue=${tracks.size}|userQueue=${queue.state.value.items.size}|PASS")
    }

    private fun runFirstSelectionAttempt(label: String) {
        val tracks = loadDomainQueue()
        val selected = tracks[EXPECTED_SELECTED_INDEX]
        val resolution = PlaybackWindowResolver.resolve(tracks, selected.entryId) { item ->
            CatalogPlaybackLocationResolver(runtime!!.database).resolve(item.trackId).firstOrNull()
        } ?: error("selected Ghost Context item could not be projected")
        val playable = resolution.items.mapNotNull { item ->
            resolution.locations[item.entryId]?.takeIf(PlaybackLocation::accessible)?.let { item to it }
        }
        assumeTrue("Selected playback window has no readable SAF locations", playable.isNotEmpty())
        val selectedPlayableIndex = playable.indexOfFirst { it.first.entryId == selected.entryId }
        assumeTrue("Selected item is not playable in the bounded window", selectedPlayableIndex >= 0)

        val mediaItems = playable.map { (item, location) ->
            MediaItem.Builder()
                .setMediaId(item.entryId)
                .setUri(location.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .build(),
                )
                .build()
        }
        val trace = PlaybackTrace(label)
        val session = MediaController.Builder(
            context,
            SessionToken(context, android.content.ComponentName(context, PlaybackService::class.java)),
        ).buildAsync()
        val mediaController = session.get(15, TimeUnit.SECONDS)
        controller = mediaController
        onMain { mediaController.addListener(trace) }
        onMain {
            mediaController.pause()
            mediaController.clearMediaItems()
        }
        assumeTrue("Player did not reach a known idle/paused baseline", waitForBaseline(mediaController))

        trace.commandIssued = SystemClock.elapsedRealtimeNanos()
        onMain { mediaController.setMediaItems(mediaItems, selectedPlayableIndex, 0L) }
        trace.commandReturned = SystemClock.elapsedRealtimeNanos()
        onMain {
            mediaController.prepare()
            mediaController.play()
        }

        assertTrue("READY callback alınmadı", trace.ready.await(20, TimeUnit.SECONDS))
        assertTrue("PLAYING callback alınmadı", trace.playing.await(20, TimeUnit.SECONDS))
        assertTrue("Pozisyon ilerlemesi gözlenmedi", waitForAdvancingPosition(mediaController, trace))

        val currentMediaId = onMain { mediaController.currentMediaItem?.mediaId }
        val media3WindowSize = onMain { mediaController.mediaItemCount }
        assertTrue("Selected Media3 item mismatch", currentMediaId == selected.entryId)
        assertTrue("Media3 bounded queue must remain smaller than domain queue", media3WindowSize < tracks.size)
        trace.logSummary(media3WindowSize)
    }

    private fun startPlayableSession(): PlaybackSession {
        val tracks = loadDomainQueue()
        val selected = tracks[EXPECTED_SELECTED_INDEX]
        val resolution = PlaybackWindowResolver.resolve(tracks, selected.entryId) { item ->
            CatalogPlaybackLocationResolver(runtime!!.database).resolve(item.trackId).firstOrNull()
        } ?: error("selected Ghost Context item could not be projected")
        val playable = resolution.items.mapNotNull { item ->
            resolution.locations[item.entryId]?.takeIf(PlaybackLocation::accessible)?.let { item to it }
        }
        assumeTrue("Selected playback window has no readable SAF locations", playable.isNotEmpty())
        val selectedPlayableIndex = playable.indexOfFirst { it.first.entryId == selected.entryId }
        assumeTrue("Selected item is not playable in the bounded window", selectedPlayableIndex >= 0)
        val mediaItems = playable.map { (item, location) ->
            MediaItem.Builder()
                .setMediaId(item.entryId)
                .setUri(location.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .setDurationMs(item.durationMs)
                        .build(),
                )
                .build()
        }
        val trace = PlaybackTrace("regression")
        val future = MediaController.Builder(
            context,
            SessionToken(context, android.content.ComponentName(context, PlaybackService::class.java)),
        ).buildAsync()
        val mediaController = future.get(15, TimeUnit.SECONDS)
        controller = mediaController
        onMain { mediaController.addListener(trace) }
        onMain {
            mediaController.pause()
            mediaController.clearMediaItems()
            mediaController.setMediaItems(mediaItems, selectedPlayableIndex, 0L)
            mediaController.repeatMode = Player.REPEAT_MODE_OFF
            mediaController.shuffleModeEnabled = false
            mediaController.prepare()
            mediaController.play()
        }
        assertTrue("Regression session did not become READY", trace.ready.await(20, TimeUnit.SECONDS))
        assertTrue("Regression session did not become PLAYING", trace.playing.await(20, TimeUnit.SECONDS))
        assertTrue("Regression session position did not advance", waitForAdvancingPosition(mediaController, trace))
        return PlaybackSession(mediaController, trace, playable, selectedPlayableIndex)
    }

    private fun waitForMediaId(player: MediaController, expectedId: String, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (onMain { player.currentMediaItem?.mediaId } == expectedId) return true
            Thread.sleep(50L)
        }
        return false
    }

    private fun waitForPlaying(player: MediaController, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (onMain { player.isPlaying }) return true
            Thread.sleep(50L)
        }
        return false
    }

    private fun logRegression(scenario: String, session: PlaybackSession, currentEntryId: String) {
        android.util.Log.i(
            TAG,
            "REGRESSION|scenario=$scenario|domainQueue=$EXPECTED_TRACK_COUNT|" +
                "media3Window=${onMain { session.controller.mediaItemCount }}|" +
                "currentEntry=$currentEntryId|playing=${onMain { session.controller.isPlaying }}|PASS",
        )
    }

    private data class PlaybackSession(
        val controller: MediaController,
        val trace: PlaybackTrace,
        val playableItems: List<Pair<QueueItem, PlaybackLocation>>,
        val selectedPlayableIndex: Int,
    )

    private fun waitForBaseline(player: MediaController): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val baseline = onMain { !player.isPlaying && player.playbackState != Player.STATE_BUFFERING }
            if (baseline) return true
            Thread.sleep(50L)
        }
        return onMain { !player.isPlaying }
    }

    private fun waitForAdvancingPosition(player: MediaController, trace: PlaybackTrace): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 20_000L
        var previous = onMain { player.currentPosition }
        while (SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(100L)
            val current = onMain { player.currentPosition }
            if (current > previous) {
                trace.positionAdvancing = SystemClock.elapsedRealtimeNanos()
                return true
            }
            previous = current
        }
        return false
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        return requireNotNull(result)
    }

    private fun loadDomainQueue(): List<QueueItem> {
        val rows = runtime!!.database.libraryDao().loadTracks(0, EXPECTED_TRACK_COUNT)
        return rows.map { row ->
            QueueItem(
                trackId = row.trackId,
                title = row.title.orEmpty(),
                artist = row.artist.orEmpty(),
                album = row.album.orEmpty(),
                durationMs = row.durationMs,
                origin = QueueEntryOrigin.GHOST,
            )
        }
    }

    private class PlaybackTrace(private val label: String) : Player.Listener {
        val ready = java.util.concurrent.CountDownLatch(1)
        val playing = java.util.concurrent.CountDownLatch(1)
        var commandIssued: Long = 0L
        var commandReturned: Long = 0L
        var positionAdvancing: Long = 0L
        private val callbackEvents = CopyOnWriteArrayList<String>()

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                callbackEvents += "READY_CALLBACK@${SystemClock.elapsedRealtimeNanos()}"
                ready.countDown()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                callbackEvents += "PLAYING_CALLBACK@${SystemClock.elapsedRealtimeNanos()}"
                playing.countDown()
            }
        }

        fun logSummary(media3WindowSize: Int) {
            android.util.Log.i(
                TAG,
                "DETERMINISTIC_PLAYBACK|label=$label|commandIssued=$commandIssued|" +
                    "commandReturned=$commandReturned|callbacks=${callbackEvents.joinToString(",")}|" +
                    "positionAdvancingPolling=$positionAdvancing|domainQueue=$EXPECTED_TRACK_COUNT|" +
                    "media3Window=$media3WindowSize",
            )
        }

        private companion object {
            const val TAG = "SmoothMediaDeterministicPlayback"
        }
    }

    private companion object {
        const val TAG = "SmoothMediaDeterministicRegression"
        const val EXPECTED_TRACK_COUNT = 7_036
        const val EXPECTED_SELECTED_INDEX = 3_517
    }
}
