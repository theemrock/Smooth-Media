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
    fun availabilityPresentation_dimsEveryStateWithoutAvailableLocation() {
        listOf(
            LibraryLocationState.MISSING,
            LibraryLocationState.UNAVAILABLE,
            LibraryLocationState.PERMISSION_LOST,
            LibraryLocationState.UNKNOWN,
        ).forEach { state ->
            val song = song("track-${state.name}").copy(locationState = state)
            assertEquals(false, song.isAccessible)
            assertTrue(song.accessibilityDescription.contains("unavailable", ignoreCase = true) ||
                song.accessibilityDescription.contains("unknown", ignoreCase = true))
        }
        assertEquals(true, song("available").isAccessible)
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
    fun artistCollectionRow_exposesRealStats_andOmitsZeroFormatLabels() {
        val row = LibraryCollectionRow(
            key = "artist-1",
            title = "Artist",
            subtitle = null,
            trackCount = 7,
            albumCount = 3,
            mp3Count = 7,
            flacCount = 0,
        )

        assertEquals("3 albums · 7 songs", row.artistStatsLine)
        assertEquals(listOf("7 MP3"), row.artistFormatLabels)
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

    @Test
    fun libraryViewModel_clearingFilter_resetsPagingAndHeading() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val viewModel = LibraryViewModel(
            pageLoader = { offset, limit ->
                calls += offset to limit
                LibraryPage(listOf(song("all-$offset")), hasMore = false)
            },
            totalLoader = { 1 },
            filteredPageLoader = { _, offset, limit ->
                calls += offset to limit
                LibraryPage(listOf(song("filtered-$offset")), hasMore = false)
            },
            filteredTotalLoader = { 1 },
        )

        viewModel.loadInitial(LibraryTrackFilter.Artist("Faithless"), "Faithless", pageSize = 1)
        viewModel.loadInitial(null, "Your library", pageSize = 1)

        assertEquals(listOf(0 to 1, 0 to 1), calls)
        assertEquals("Your library", viewModel.state.value.heading)
        assertEquals(false, viewModel.state.value.isFiltered)
    }

    @Test
    fun snapshotCurrentListUsesActiveFilterAndStablePageOrder() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val viewModel = LibraryViewModel(
            pageLoader = { _, _ -> error("unfiltered loader must not be used") },
            totalLoader = { 0 },
            filteredPageLoader = { _, offset, limit ->
                calls += offset to limit
                when (offset) {
                    0 -> LibraryPage(listOf(song("filtered-1"), song("filtered-2")), hasMore = true)
                    2 -> LibraryPage(listOf(song("filtered-3")), hasMore = false)
                    else -> LibraryPage(emptyList(), hasMore = false)
                }
            },
            filteredTotalLoader = { 3 },
        )

        viewModel.loadInitial(LibraryTrackFilter.Artist("Artist"), "Artist", pageSize = 2)

        assertEquals(listOf("filtered-1", "filtered-2", "filtered-3"), viewModel.snapshotCurrentList().map { it.trackId })
        assertEquals(listOf(0 to 2, 0 to 2, 2 to 2), calls)
    }

    @Test
    fun snapshotCurrentListSupports7049TracksWithoutUsingLoadedPageOnly() {
        val viewModel = LibraryViewModel(
            pageLoader = { offset, limit ->
                val remaining = (7049 - offset).coerceAtLeast(0)
                val count = minOf(limit, remaining)
                LibraryPage(
                    songs = (0 until count).map { index -> song("track-${offset + index}") },
                    hasMore = offset + count < 7049,
                )
            },
            totalLoader = { 7049 },
        )
        viewModel.loadInitial(pageSize = 50)

        val snapshot = viewModel.snapshotCurrentList()

        assertEquals(7049, snapshot.size)
        assertEquals("track-0", snapshot.first().trackId)
        assertEquals("track-3524", snapshot[3524].trackId)
        assertEquals("track-7048", snapshot.last().trackId)
    }

    @Test
    fun scrollPositionBelongsToViewModelAndResetsForNewSource() {
        val viewModel = LibraryViewModel(
            pageLoader = { _, _ -> LibraryPage(listOf(song("track-1")), false) },
            totalLoader = { 1 },
        )

        viewModel.loadInitial()
        viewModel.saveScrollPosition(17, 23)
        assertEquals(17, viewModel.scrollIndex)
        assertEquals(23, viewModel.scrollOffset)

        viewModel.loadInitial()
        assertEquals(0, viewModel.scrollIndex)
        assertEquals(0, viewModel.scrollOffset)
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
