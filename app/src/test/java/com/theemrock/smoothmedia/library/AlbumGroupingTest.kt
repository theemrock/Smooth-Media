package com.theemrock.smoothmedia.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AlbumGroupingTest {
    @Test
    fun differentTrackArtistsWithSameAlbumArtistAndScopeShareOneCompilation() {
        val first = albumGroupingKey(AlbumGroupingInput("Blues Brothers 2000", "Artist A", "", "source-1", "folder-a/"))
        val second = albumGroupingKey(AlbumGroupingInput("Blues Brothers 2000", "Artist B", "", "source-1", "folder-a/"))
        assertEquals(first, second)
    }

    @Test
    fun sameAlbumAndSameArtistInSameScopeShareOneAlbum() {
        val first = albumGroupingKey(AlbumGroupingInput("Album", "Artist", "Artist", "source-1", "folder-a/"))
        val second = albumGroupingKey(AlbumGroupingInput("Album", "Artist", "Artist", "source-1", "folder-a/"))
        assertEquals(first, second)
    }

    @Test
    fun sameAlbumNameInDifferentScopeDoesNotMerge() {
        val first = albumGroupingKey(AlbumGroupingInput("Album", "Artist A", "", "source-1", "folder-a/"))
        val second = albumGroupingKey(AlbumGroupingInput("Album", "Artist B", "", "source-2", "folder-b/"))
        assertNotEquals(first, second)
    }

    @Test
    fun albumDetailFilterRetainsTheGroupingScope() {
        val key = AlbumGroupingKey.decode(
            albumGroupingKey(AlbumGroupingInput("Album", "Artist", "", "source-1", "folder-a/")),
        )
        assertEquals(LibraryTrackFilter.Album("Album", "", "source-1", "folder-a/"), key.toFilter())
    }

    @Test
    fun missingAlbumArtistWithMultipleTrackArtistsIsReportedWithoutChangingTheKey() {
        val rows = listOf(
            AlbumGroupingInput("Compilation", "Artist A", "", "source-1", "folder-a/"),
            AlbumGroupingInput("Compilation", "Artist B", "", "source-1", "folder-a/"),
        )

        val diagnostics = findSuspiciousAlbumGroupings(rows)

        assertEquals(1, diagnostics.size)
        assertEquals(setOf("Artist A", "Artist B"), diagnostics.single().trackArtists)
        assertEquals("Compilation", diagnostics.single().album)
    }
}
