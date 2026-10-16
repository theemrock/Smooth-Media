package com.theemrock.smoothmedia.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackLocationSelectorTest {
    @Test
    fun selectsAvailableFlacBeforeUnavailableFlacAndMp3() {
        val selected = PlaybackLocationSelector.select(
            listOf(
                PlaybackLocation("t", "z", "content://mp3", PlaybackFormat.MP3, false),
                PlaybackLocation("t", "b", "content://flac", PlaybackFormat.FLAC, true),
                PlaybackLocation("t", "a", "content://flac-2", PlaybackFormat.FLAC, false),
            ),
        )

        assertEquals("b", selected?.locationId)
    }

    @Test
    fun noAccessibleLocationReturnsNullWithoutChangingCatalogState() {
        assertNull(PlaybackLocationSelector.select(listOf(PlaybackLocation("t", "a", "content://x", PlaybackFormat.MP3, false))))
    }
}
