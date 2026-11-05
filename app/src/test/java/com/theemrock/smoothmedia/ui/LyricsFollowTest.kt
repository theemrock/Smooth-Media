package com.theemrock.smoothmedia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsFollowTest {
    @Test
    fun swipeZoneIsMiddleSeventyPercent() {
        assertFalse(isInLyricsSwipeZone(100f, 1000))
        assertTrue(isInLyricsSwipeZone(150f, 1000))
        assertTrue(isInLyricsSwipeZone(500f, 1000))
        assertTrue(isInLyricsSwipeZone(850f, 1000))
        assertFalse(isInLyricsSwipeZone(900f, 1000))
        assertFalse(isInLyricsSwipeZone(10f, 0))
    }

    @Test
    fun centerOffsetUsesMeasuredHeight() {
        assertEquals(-900, lyricsCenterScrollOffset(2000, 200))
        assertEquals(-880, lyricsCenterScrollOffset(2000, 240))
    }

    @Test
    fun heightFallsBackToVisibleAverageThenDefault() {
        assertEquals(120, estimateLyricsItemHeight(120, listOf(100, 200)))
        assertEquals(150, estimateLyricsItemHeight(null, listOf(100, 200)))
        assertEquals(150, estimateLyricsItemHeight(0, listOf(100, 200)))
        assertEquals(80, estimateLyricsItemHeight(null, emptyList(), 80))
    }
}
