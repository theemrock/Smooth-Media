package com.theemrock.smoothmedia.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualLyricsTest {
    @Test
    fun timestampedTextIsStoredAsSynced() {
        val lyrics = ManualLyrics.build("t1", "[00:12.50] first\r\n[00:20.00] second", 42L)!!
        assertEquals(LyricsMode.SYNCED, lyrics.mode)
        assertEquals(LyricsProvenance.USER_OVERRIDE, lyrics.provenance)
        assertEquals("[00:12.50] first\n[00:20.00] second", lyrics.text)
        assertEquals(42L, lyrics.updatedAtEpochMs)
    }

    @Test
    fun untimedTextIsStoredAsPlain() {
        assertEquals(LyricsMode.PLAIN, ManualLyrics.build("t1", "just words\nmore words", 1L)!!.mode)
    }

    @Test
    fun blankInputIsRejected() {
        assertNull(ManualLyrics.build("t1", "  \n\t ", 1L))
    }
}
