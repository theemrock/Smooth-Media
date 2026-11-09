package com.theemrock.smoothmedia.eq

import org.junit.Assert.assertEquals
import org.junit.Test

class ScannedSongLabelTest {
    @Test
    fun tagTitleWins() {
        assertEquals("Real Title", ScannedSongLabel.title("Real Title", "content://x/document/primary%3AMusic%2Ffile.mp3"))
    }

    @Test
    fun blankTitleFallsBackToFileNameWithoutExtension() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FAlbum%2FAda+Dan%C4%B1%C5%9F.mp3"
        assertEquals("Ada+Danış", ScannedSongLabel.title("", uri))
    }

    @Test
    fun missingLocatorGivesEmpty() {
        assertEquals("", ScannedSongLabel.title(" ", null))
    }
}
