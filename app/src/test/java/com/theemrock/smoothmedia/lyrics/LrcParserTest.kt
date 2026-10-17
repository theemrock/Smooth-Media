package com.theemrock.smoothmedia.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesUnicodeMultipleTimestampsAndOffset() {
        val result = LrcParser.parse("[ar:Sanatçı]\n[offset:100]\n[00:01.20][00:02.00]Çizgi\n[00:03:04.50]Son")

        assertEquals("Sanatçı", result.metadata["ar"])
        assertEquals(1_300L, result.lines[0].startMs)
        assertEquals(2_100L, result.lines[1].startMs)
        assertEquals(184_600L, result.lines[2].startMs)
        assertEquals("Çizgi", result.lines[0].text)
    }

    @Test
    fun activeLineUsesLatestTimestampAndIgnoresMalformedInput() {
        val result = LrcParser.parse("garbage\n[00:01.00]Bir\n[00:03.00]İki\n[00:02.00]Ara")

        assertEquals(0, result.activeLineAt(1_500L))
        assertEquals(1, result.activeLineAt(2_500L))
        assertEquals(2, result.activeLineAt(3_500L))
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun duplicateTimestampsRemainDeterministicAndSeekIsSafe() {
        val result = LrcParser.parse("[00:01.00]A\n[00:01.00]B\n[00:02.00]C")

        assertEquals(listOf("A", "B", "C"), result.lines.map { it.text })
        assertEquals(1_000L, result.seekTargetForLine(1))
        assertEquals(null, result.seekTargetForLine(9))
    }
}
