package com.theemrock.smoothmedia.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCollectionSelectionTest {
    @Test
    fun emptySelectionAndToggleAreDeterministic() {
        assertEquals(emptySet<String>(), toggleCollectionSelection(emptySet(), "album-a") - "album-a")
        assertEquals(setOf("album-a"), toggleCollectionSelection(emptySet(), "album-a"))
        assertEquals(emptySet<String>(), toggleCollectionSelection(setOf("album-a"), "album-a"))
    }

    @Test
    fun selectedRowsKeepStableCollectionKeys() {
        val rows = listOf(
            LibraryCollectionRow("album-source-a", "Same", "Artist A", 1),
            LibraryCollectionRow("album-source-b", "Same", "Artist B", 1),
        )

        assertEquals(listOf("album-source-b"), selectedCollectionRows(rows, setOf("album-source-b")).map { it.key })
    }

    @Test
    fun duplicateSongsAcrossSelectedCollectionsAreDeduplicatedByTrackId() {
        val first = selectionSong("track-1", "First")
        val duplicate = selectionSong("track-1", "Duplicate")
        val second = selectionSong("track-2", "Second")

        assertEquals(
            listOf("track-1", "track-2"),
            deduplicateLibrarySongs(listOf(listOf(first), listOf(duplicate, second))).map { it.trackId },
        )
    }

    private fun selectionSong(trackId: String, title: String) = LibrarySong(
        trackId = trackId,
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 1_000,
        metadataState = LibraryMetadataState.COMPLETE,
        locationState = LibraryLocationState.AVAILABLE,
        locationCount = 1,
    )
}
