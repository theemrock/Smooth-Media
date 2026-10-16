package com.theemrock.smoothmedia.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AllSongsLayoutTest {
    @Test
    fun allSongsArtworkSizeStaysCompactForPhoneWidth() {
        assertEquals(52f, allSongsArtworkSizeDp(384f), 0.01f)
        assertEquals(64f, allSongsArtworkSizeDp(600f), 0.01f)
    }
}
