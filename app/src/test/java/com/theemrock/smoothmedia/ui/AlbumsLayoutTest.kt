package com.theemrock.smoothmedia.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumsLayoutTest {
    @Test
    fun albumsGridUsesFourCompactColumnsOnPhoneWidth() {
        assertEquals(80f, albumsGridMinCardWidthDp(), 0.01f)
        assertEquals(4, albumsGridColumnCount(352f))
    }
}
