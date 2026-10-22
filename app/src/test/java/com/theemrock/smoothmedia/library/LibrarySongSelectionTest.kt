package com.theemrock.smoothmedia.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySongSelectionTest {
    @Test
    fun toggleKeepsFirstSelectionOrderAndReSelectingAppendsAtEnd() {
        val selection = LibrarySongSelection()
            .toggle("track-2")
            .toggle("track-1")
            .toggle("track-2")
            .toggle("track-2")

        assertEquals(listOf("track-1", "track-2"), selection.trackIds)
    }

    @Test
    fun clearRemovesAllSelectedTrackIds() {
        assertEquals(
            emptyList<String>(),
            LibrarySongSelection(listOf("track-1", "track-2")).clear().trackIds,
        )
    }

    @Test
    fun reconcileRemovesDeletedIdsWithoutLoadingUnselectedSongs() {
        val selection = LibrarySongSelection(listOf("track-1", "deleted", "track-2"))

        assertEquals(
            listOf("track-1", "track-2"),
            selection.reconcile(setOf("track-1", "track-2", "track-3")).trackIds,
        )
    }

    @Test
    fun selectedSongsFollowStableTrackIdOrderAndIgnoreDuplicates() {
        val songs = listOf(song("track-2"), song("track-1"), song("track-2"))

        assertEquals(
            listOf("track-1", "track-2"),
            selectedLibrarySongs(songs, listOf("track-1", "track-2")).map { it.trackId },
        )
    }

    private fun song(trackId: String) = LibrarySong(
        trackId = trackId,
        title = trackId,
        artist = "Artist",
        album = "Album",
        durationMs = 1_000,
        metadataState = LibraryMetadataState.COMPLETE,
        locationState = LibraryLocationState.AVAILABLE,
        locationCount = 1,
    )
}
