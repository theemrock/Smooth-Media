package com.theemrock.smoothmedia.ui

import com.theemrock.smoothmedia.playback.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingControlsTest {
    @Test
    fun repeatOneShowsSuffixAndOffDoesNot() {
        assertEquals("↻", repeatModeGlyph(RepeatMode.OFF))
        assertEquals("↻", repeatModeGlyph(RepeatMode.ALL))
        assertEquals("↻1", repeatModeGlyph(RepeatMode.ONE))
    }

    @Test
    fun repeatIsActiveUnlessOff() {
        assertFalse(isRepeatActive(RepeatMode.OFF))
        assertTrue(isRepeatActive(RepeatMode.ALL))
        assertTrue(isRepeatActive(RepeatMode.ONE))
    }
}
