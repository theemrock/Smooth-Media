package com.theemrock.smoothmedia.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistActionsTest {
    @Test
    fun keepsSelectionOrderAndDeduplicates() {
        assertEquals(
            listOf("track-3", "track-1", "track-2"),
            orderedUniqueTrackIds(listOf("track-3", "", "track-1", "track-3", "track-2")),
        )
    }
}
