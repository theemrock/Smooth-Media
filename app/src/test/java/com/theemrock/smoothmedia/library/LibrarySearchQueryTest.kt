package com.theemrock.smoothmedia.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchQueryTest {
    @Test
    fun blankQueryIsNotSearchable() {
        assertEquals(null, LibrarySearchQuery.normalize("   "))
    }

    @Test
    fun queryIsTrimmedAndUsesLiteralMatchArgument() {
        val query = LibrarySearchQuery.normalize("  metal  ")
        assertEquals("metal", query)
        assertEquals(query, LibrarySearchQuery.likeArgument(query!!))
    }

    @Test
    fun literalArgumentDoesNotTurnUserTextIntoSqlWildcard() {
        assertEquals("Metallica", LibrarySearchQuery.likeArgument("Metallica"))
        assertEquals("Metal Militia", LibrarySearchQuery.likeArgument("Metal Militia"))
    }

    @Test
    fun snapshotKeyIncludesQueryAndVisibleTrackOrder() {
        val first = LibrarySong("track-a", "A", "Artist", "Album", null, LibraryMetadataState.COMPLETE, LibraryLocationState.AVAILABLE, 1)
        val second = first.copy(trackId = "track-b")
        assertEquals("search:metal:track-a,track-b", LibrarySearchQuery.snapshotKey(" metal ", listOf(first, second)))
    }

    @Test
    fun searchSqlContainsEffectiveMetadataAndStableTrackOrdering() {
        val sql = LibrarySearchQuery.tracksSql()
        assertTrue(sql.contains("COALESCE(title_override.value,m.normalizedTitle)"))
        assertTrue(sql.contains("COALESCE(artist_override.value,m.normalizedArtist)"))
        assertTrue(sql.contains("COALESCE(album_override.value,m.normalizedAlbum)"))
        assertTrue(sql.contains("l.path"))
        assertTrue(sql.contains("ORDER BY"))
        assertTrue(sql.contains("t.track_id"))
    }
}
