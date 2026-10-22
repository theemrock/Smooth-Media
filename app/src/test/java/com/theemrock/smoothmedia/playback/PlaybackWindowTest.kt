package com.theemrock.smoothmedia.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackWindowTest {
    @Test
    fun selectCentersSelectedEntryWithOnePreviousAndTwoFollowingEntries() {
        val items = queueItems(7)

        val window = requireNotNull(PlaybackWindow.select(items, selectedEntryId = "entry-3"))

        assertEquals(listOf("entry-2", "entry-3", "entry-4", "entry-5"), window.items.map { it.entryId })
        assertEquals(1, window.selectedIndex)
    }

    @Test
    fun selectClampsAtFirstAndLastEntryWithoutChangingDomainOrder() {
        val items = queueItems(4)

        val first = requireNotNull(PlaybackWindow.select(items, selectedEntryId = "entry-0"))
        val last = requireNotNull(PlaybackWindow.select(items, selectedEntryId = "entry-3"))

        assertEquals(listOf("entry-0", "entry-1", "entry-2"), first.items.map { it.entryId })
        assertEquals(0, first.selectedIndex)
        assertEquals(listOf("entry-2", "entry-3"), last.items.map { it.entryId })
        assertEquals(1, last.selectedIndex)
        assertEquals((0..3).map { "entry-$it" }, items.map { it.entryId })
    }

    @Test
    fun selectKeepsLargeDomainQueueOutsideTheBoundedPlaybackProjection() {
        val items = queueItems(7_036)

        val window = requireNotNull(PlaybackWindow.select(items, selectedEntryId = "entry-3518"))

        assertEquals(7_036, items.size)
        assertEquals(listOf("entry-3517", "entry-3518", "entry-3519", "entry-3520"), window.items.map { it.entryId })
        assertEquals(1, window.selectedIndex)
    }

    @Test
    fun selectReturnsNoWindowWhenSelectedEntryIsNotInDomainQueue() {
        assertNull(PlaybackWindow.select(queueItems(2), selectedEntryId = "missing"))
    }

    @Test
    fun resolveProjectsOnlyTheBoundedWindowFromALargeGhostContext() {
        val items = queueItems(7_036)
        val resolvedTrackIds = mutableListOf<String>()

        val projection = PlaybackWindowResolver.resolve(items, selectedEntryId = "entry-3518") { item ->
            resolvedTrackIds += item.trackId
            PlaybackLocation(item.trackId, "location-${item.trackId}", "content://${item.trackId}", PlaybackFormat.MP3, true)
        }

        assertEquals(7_036, items.size)
        assertEquals(listOf("track-3517", "track-3518", "track-3519", "track-3520"), resolvedTrackIds)
        assertEquals(listOf("entry-3517", "entry-3518", "entry-3519", "entry-3520"), projection?.items?.map { it.entryId })
        assertEquals(1, projection?.startIndex)
    }

    @Test
    fun refreshIsRequestedBeforePlaybackReachesTheLastLookAheadItem() {
        val activeWindowEntryIds = listOf("entry-2", "entry-3", "entry-4", "entry-5")

        assertEquals(false, PlaybackWindow.needsRefresh(activeWindowEntryIds, "entry-3", hasFollowingDomainItem = true))
        assertEquals(true, PlaybackWindow.needsRefresh(activeWindowEntryIds, "entry-4", hasFollowingDomainItem = true))
        assertEquals(false, PlaybackWindow.needsRefresh(activeWindowEntryIds, "entry-5", hasFollowingDomainItem = false))
    }

    private fun queueItems(size: Int): List<QueueItem> = (0 until size).map { index ->
        QueueItem(trackId = "track-$index", entryId = "entry-$index")
    }
}
