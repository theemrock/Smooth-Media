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
    fun conflictingTrackLevelAlbumArtistsShareOneCompilationScope() {
        val first = AlbumGroupingInput("Blues Brothers 2000", "Artist A", "Artist A", "source-1", "folder-a/")
        val second = AlbumGroupingInput("Blues Brothers 2000", "Artist B", "Artist B", "source-1", "folder-a/")

        assertEquals(
            setOf("Blues Brothers 2000\u001Esource-1\u001Efolder-a/"),
            setOf(first, second).map { it.album + "\u001E" + it.sourceId + "\u001E" + it.folderPrefix }.toSet(),
        )
    }

    @Test
    fun conflictingAlbumArtistsAreReportedForTheSharedScope() {
        val diagnostics = findSuspiciousAlbumGroupings(
            listOf(
                AlbumGroupingInput("Compilation", "Artist A", "Album Artist A", "source-1", "folder-a/"),
                AlbumGroupingInput("Compilation", "Artist B", "Album Artist B", "source-1", "folder-a/"),
            ),
        )

        assertEquals(setOf("Album Artist A", "Album Artist B"), diagnostics.single().albumArtists)
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
    fun roomQueryStyleKeyDecodesToTheSameAlbumFilter() {
        val key = AlbumGroupingKey.decode("Load\u001FMetallica\u001Esource-1\u001Efolder-a/")

        assertEquals(LibraryTrackFilter.Album("Load", "Metallica", "source-1", "folder-a/"), key.toFilter())
    }

    @Test
    fun normalAlbumKeepsAllFourteenTracksInOneGrouping() {
        val tracks = (1..14).map { index ->
            AlbumGroupingInput("Load", "Metallica", "Metallica", "source-1", "folder-a/") to index
        }

        assertEquals(1, tracks.groupBy { albumGroupingKey(it.first) }.size)
        assertEquals(14, tracks.size)
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
