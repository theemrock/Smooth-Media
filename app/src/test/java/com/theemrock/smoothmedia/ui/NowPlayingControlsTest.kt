package com.theemrock.smoothmedia.ui

import com.theemrock.smoothmedia.playback.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingControlsTest {
    @Test
    fun repeatBadgePerMode() {
        assertNull(repeatBadge(RepeatMode.OFF))
        assertEquals("A", repeatBadge(RepeatMode.ALL))
        assertEquals("1", repeatBadge(RepeatMode.ONE))
    }

    @Test
    fun repeatIsActiveUnlessOff() {
        assertFalse(isRepeatActive(RepeatMode.OFF))
        assertTrue(isRepeatActive(RepeatMode.ALL))
        assertTrue(isRepeatActive(RepeatMode.ONE))
    }

    @Test
    fun dismissIgnoredInTopTenPercent() {
        assertFalse(isNowPlayingDismissStart(50f, 1000))
        assertTrue(isNowPlayingDismissStart(100f, 1000))
        assertTrue(isNowPlayingDismissStart(500f, 1000))
    }
}
