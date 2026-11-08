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

    @Test
    fun lyricsSqlMatchesLyricsTextOnlyAndKeepsTrackColumnsAndPaging() {
        val sql = LibrarySearchQuery.lyricsTracksSql()
        assertTrue(sql.contains("JOIN lyrics_cache lc ON lc.trackId=t.track_id"))
        assertTrue(sql.contains("instr(lower(lc.text),lower(?))>0"))
        assertTrue(sql.contains("COALESCE(title_override.value,m.normalizedTitle) AS title"))
        assertTrue(sql.trimEnd().endsWith("LIMIT ? OFFSET ?"))
        assertEquals(3, sql.count { it == '?' })
    }

    @Test
    fun lyricSnippetStripsTimestampsAndIgnoresTimestampOnlyMatches() {
        val lrc = "[ar:Someone]\n[00:12.30]Aşk Yangını başladı\n[00:15.00]Yine de sev"
        assertEquals("Aşk Yangını başladı", LibrarySearchQuery.lyricSnippet(lrc, "yangını"))
        assertEquals(null, LibrarySearchQuery.lyricSnippet(lrc, "12"))
    }
}
