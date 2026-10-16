package com.theemrock.smoothmedia.ui

import com.theemrock.smoothmedia.library.LibraryLocationState
import com.theemrock.smoothmedia.library.LibraryMetadataState
import com.theemrock.smoothmedia.library.LibrarySong
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryDetailLayoutTest {
    @Test
    fun detailSongsHaveStableTitleThenTrackIdOrder() {
        val songs = listOf(
            LibrarySong("2", "Same", "A", "Album", null, LibraryMetadataState.COMPLETE, LibraryLocationState.UNKNOWN, 1),
            LibrarySong("1", "Same", "A", "Album", null, LibraryMetadataState.COMPLETE, LibraryLocationState.UNKNOWN, 1),
            LibrarySong("3", "Another", "A", "Album", null, LibraryMetadataState.COMPLETE, LibraryLocationState.UNKNOWN, 1),
        )
        assertEquals(listOf("3", "1", "2"), detailSongsStableOrder(songs).map { it.trackId })
    }
}
