package com.theemrock.smoothmedia.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRepositoryTest {
    @Test
    fun mapper_prefersFieldOverride_overNormalizedMetadata() {
        val song = LibraryTrackRow(
            trackId = "track-1",
            title = "Room title",
            artist = "Room artist",
            album = null,
            durationMs = 125_000L,
            metadataPresent = 1,
            titleStatus = "AVAILABLE",
            artistStatus = "AVAILABLE",
            albumStatus = "MISSING",
            locationState = "AVAILABLE",
            locationCount = 2,
        ).toLibrarySong()

        assertEquals("Room title", song.title)
        assertEquals("Room artist", song.artist)
        assertEquals("Unknown album", song.album)
        assertEquals(LibraryMetadataState.INCOMPLETE, song.metadataState)
        assertEquals(LibraryLocationState.AVAILABLE, song.locationState)
        assertEquals(2, song.locationCount)
    }

    @Test
    fun mapper_usesDisplayFallback_withoutInventingMetadata() {
        val song = LibraryTrackRow(
            trackId = "track-missing",
            title = null,
            artist = null,
            album = null,
            durationMs = null,
            metadataPresent = 0,
            titleStatus = null,
            artistStatus = null,
            albumStatus = null,
            locationState = "MISSING",
            locationCount = 1,
        ).toLibrarySong()

        assertEquals("Unknown title", song.title)
        assertEquals("Unknown artist", song.artist)
        assertEquals("Unknown album", song.album)
        assertEquals(LibraryMetadataState.MISSING, song.metadataState)
        assertEquals(LibraryLocationState.MISSING, song.locationState)
    }

    @Test
    fun viewModel_loadsPagedState_andNeverLoadsAllTracksAtOnce() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val pages = mapOf(
            0 to listOf(song("track-1"), song("track-2")),
            2 to listOf(song("track-3")),
        )
        val viewModel = LibraryViewModel(
            pageLoader = { offset, limit ->
                calls += offset to limit
                LibraryPage(pages[offset].orEmpty(), hasMore = offset == 0)
            },
            totalLoader = { 3 },
        )

        viewModel.loadInitial(pageSize = 2)
        viewModel.loadNextPage()

        assertEquals(listOf("track-1", "track-2", "track-3"), viewModel.state.value.songs.map { it.trackId })
        assertEquals(3, viewModel.state.value.totalTracks)
        assertTrue(viewModel.state.value.loaded)
        assertEquals(listOf(0 to 2, 2 to 2), calls)
    }

    @Test
    fun collectionViewModel_loadsPagedCollectionState() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val viewModel = LibraryCollectionViewModel(
            pageLoader = { _, offset, limit ->
                calls += offset to limit
                LibraryCollectionPage(
                    collections = if (offset == 0) {
                        listOf(LibraryCollectionRow("album-1", "Album", "Artist", 2))
                    } else {
                        emptyList()
                    },
                    hasMore = false,
                )
            },
            totalLoader = { 1 },
        )

        viewModel.loadInitial(LibraryCollectionType.ALBUMS, pageSize = 1)

        assertEquals(LibraryCollectionType.ALBUMS, viewModel.state.value.type)
        assertEquals(1, viewModel.state.value.totalCollections)
        assertEquals(listOf("Album"), viewModel.state.value.collections.map { it.title })
        assertEquals(listOf(0 to 1), calls)
    }

    @Test
    fun viewModels_stopAtLastPage_withoutDuplicateRequests() {
        var songCalls = 0
        val songs = LibraryViewModel(
            pageLoader = { offset, _ ->
                songCalls++
                LibraryPage(listOf(song("song-$offset")), hasMore = offset == 0)
            },
            totalLoader = { 1 },
        )
        songs.loadInitial(pageSize = 1)
        songs.loadNextPage()
        songs.loadNextPage()
        assertEquals(2, songCalls)
        assertEquals(listOf("song-0", "song-1"), songs.state.value.songs.map { it.trackId })

        var collectionCalls = 0
        val collections = LibraryCollectionViewModel(
            pageLoader = { _, offset, _ ->
                collectionCalls++
                LibraryCollectionPage(listOf(LibraryCollectionRow("key-$offset", "Title", null, 1)), offset == 0)
            },
            totalLoader = { 1 },
        )
        collections.loadInitial(LibraryCollectionType.ALBUMS, pageSize = 1)
        collections.loadNextPage()
        collections.loadNextPage()
        assertEquals(2, collectionCalls)
    }

    private fun song(id: String) = LibrarySong(
        trackId = id,
        title = id,
        artist = "Artist",
        album = "Album",
        durationMs = 1000L,
        metadataState = LibraryMetadataState.COMPLETE,
        locationState = LibraryLocationState.AVAILABLE,
        locationCount = 1,
    )
}
