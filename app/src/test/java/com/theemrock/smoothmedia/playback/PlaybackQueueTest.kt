package com.theemrock.smoothmedia.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueTest {
    @Test
    fun addQueueRejectsSameTrackAndSameEntry() {
        val queue = PlaybackQueueController()

        val first = QueueItem(trackId = "a", entryId = "entry-a")
        queue.replace(listOf(first, QueueItem("b", entryId = "entry-b")))
        assertFalse(queue.add(first))
        assertFalse(queue.add(QueueItem(trackId = "a", entryId = "entry-a-2")))
        assertEquals(listOf("a", "b"), queue.state.value.items.map { it.trackId })
    }

    @Test
    fun nextHonorsRepeatModesAtQueueEnd() {
        val queue = PlaybackQueueController()
        queue.replace(listOf(QueueItem("a"), QueueItem("b")))

        assertEquals(1, queue.next())
        assertEquals(null, queue.next())
        queue.setRepeat(RepeatMode.ALL)
        assertEquals(0, queue.next())
        queue.setRepeat(RepeatMode.ONE)
        assertEquals(0, queue.next())
    }

    @Test
    fun restoreNeverStartsPlaybackAndReorderKeepsCurrentTrack() {
        val queue = PlaybackQueueController()
        queue.replace(listOf(QueueItem("a"), QueueItem("b"), QueueItem("c")))
        queue.select(1)
        queue.reorder(1, 0)

        assertEquals("b", queue.state.value.current?.trackId)
        assertFalse(queue.state.value.autoPlayOnRestore)
    }

    @Test
    fun removeCurrentSelectsStableEntryAndPlayFromQueueChangesCurrent() {
        val queue = PlaybackQueueController()
        queue.replace(listOf(
            QueueItem(trackId = "a", entryId = "a-1"),
            QueueItem(trackId = "c", entryId = "c-1"),
            QueueItem(trackId = "b", entryId = "b-1"),
        ))
        queue.select(1)
        assertTrue(queue.remove(1))
        assertEquals("b-1", queue.state.value.current?.entryId)
        assertTrue(queue.select(0))
        assertEquals("a-1", queue.state.value.current?.entryId)
    }

    @Test
    fun playNextMovesExistingTrackWithoutCreatingDuplicate() {
        val queue = PlaybackQueueController()
        queue.replace(listOf(QueueItem("a"), QueueItem("b"), QueueItem("c")))
        assertTrue(queue.playNext(QueueItem("c")))
        assertEquals(listOf("a", "c", "b"), queue.state.value.items.map { it.trackId })
        assertEquals(0, queue.state.value.currentIndex)
    }

    @Test
    fun ghostSnapshotStartsAtSelectedTrackAndManualTakeoverDropsUnplayedGhostEntries() {
        val queue = PlaybackQueueController()
        queue.startGhost(
            items = listOf(QueueItem("a"), QueueItem("b"), QueueItem("c")),
            selectedIndex = 1,
            sourceKey = "songs:filtered",
        )

        assertEquals(QueueMode.GHOST, queue.state.value.mode)
        assertEquals("b", queue.state.value.current?.trackId)
        assertEquals(listOf("a", "b", "c"), queue.state.value.ghostContext?.trackIds)

        assertTrue(queue.takeOverUserQueue(QueueItem("manual"), playNext = false))
        assertEquals(QueueMode.USER, queue.state.value.mode)
        assertEquals(listOf("b", "manual"), queue.state.value.items.map { it.trackId })
        assertEquals(0, queue.state.value.currentIndex)
    }

    @Test
    fun playNextOnCurrentTrackDoesNotMoveOrDuplicateIt() {
        val queue = PlaybackQueueController()
        queue.replace(listOf(QueueItem("a"), QueueItem("b")))

        assertFalse(queue.playNext(QueueItem("a")))
        assertEquals(listOf("a", "b"), queue.state.value.items.map { it.trackId })
    }

    @Test
    fun shuffleUsesPersistableEntryOrderForNextAndPrevious() {
        val queue = PlaybackQueueController()
        queue.replace(listOf(QueueItem("a"), QueueItem("b"), QueueItem("c")))
        queue.select(1)
        queue.setShuffle(true)

        val order = queue.orderedForPlayback().map { it.trackId }
        assertEquals("b", order.first())
        val next = queue.next()
        assertTrue(next != null)
        assertEquals(order[1], queue.state.value.current?.trackId)
        assertEquals(1, queue.previous())
        assertEquals("b", queue.state.value.current?.trackId)
    }

    @Test
    fun clearRemovesAllEntriesWithoutAutoplay() {
        val queue = PlaybackQueueController()
        queue.replace(listOf(QueueItem("a"), QueueItem("b")))

        queue.clear()

        assertTrue(queue.state.value.items.isEmpty())
        assertEquals(-1, queue.state.value.currentIndex)
        assertFalse(queue.state.value.autoPlayOnRestore)
    }

    @Test
    fun playableProjectionMapsStartIndexAfterUnavailableEntriesAreFiltered() {
        val items = listOf(
            QueueItem("unavailable", entryId = "u"),
            QueueItem("current", entryId = "c"),
            QueueItem("next", entryId = "n"),
        )
        val location = PlaybackLocation("current", "location-c", "content://current", PlaybackFormat.MP3, true)
        val projection = PlaybackQueueProjection.project(items, mapOf("c" to location), startIndex = 1)

        assertEquals(listOf("current"), projection.items.map { it.trackId })
        assertEquals(listOf("location-c"), projection.locations.map { it.locationId })
        assertEquals(0, projection.startIndex)
    }
}
