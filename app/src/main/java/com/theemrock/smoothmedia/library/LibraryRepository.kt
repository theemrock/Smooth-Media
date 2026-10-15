package com.theemrock.smoothmedia.library

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.sqlite.db.SimpleSQLiteQuery
import com.theemrock.smoothmedia.catalog.database.dao.LibraryDao

enum class LibraryMetadataState {
    COMPLETE,
    INCOMPLETE,
    MISSING,
}

enum class LibraryLocationState {
    AVAILABLE,
    MISSING,
    UNAVAILABLE,
    PERMISSION_LOST,
    UNKNOWN,
}

data class LibrarySong(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long?,
    val metadataState: LibraryMetadataState,
    val locationState: LibraryLocationState,
    val locationCount: Int,
)

data class LibraryPage(
    val songs: List<LibrarySong>,
    val hasMore: Boolean,
)

enum class LibraryCollectionType {
    ALBUMS,
    ARTISTS,
    GENRES,
    FOLDERS,
}

data class LibraryCollectionRow(
    val key: String,
    val title: String,
    val subtitle: String?,
    val trackCount: Int,
)

data class LibraryCollectionPage(
    val collections: List<LibraryCollectionRow>,
    val hasMore: Boolean,
)

sealed interface LibraryTrackFilter {
    data class Album(val album: String, val albumArtist: String) : LibraryTrackFilter
    data class Artist(val artist: String) : LibraryTrackFilter
    data class Genre(val genre: String) : LibraryTrackFilter
    data class Folder(val path: String) : LibraryTrackFilter
}

data class LibraryTrackRow(
    val trackId: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val metadataPresent: Int,
    val titleStatus: String?,
    val artistStatus: String?,
    val albumStatus: String?,
    val locationState: String?,
    val locationCount: Int,
)

fun LibraryTrackRow.toLibrarySong(): LibrarySong = LibrarySong(
    trackId = trackId,
    title = title.orEmpty().ifBlank { "Unknown title" },
    artist = artist.orEmpty().ifBlank { "Unknown artist" },
    album = album.orEmpty().ifBlank { "Unknown album" },
    durationMs = durationMs,
    metadataState = when {
        metadataPresent == 0 -> LibraryMetadataState.MISSING
        listOf(titleStatus, artistStatus, albumStatus).all { it == "AVAILABLE" } ->
            LibraryMetadataState.COMPLETE
        else -> LibraryMetadataState.INCOMPLETE
    },
    locationState = locationState?.let { value ->
        runCatching { LibraryLocationState.valueOf(value) }.getOrDefault(LibraryLocationState.UNKNOWN)
    } ?: LibraryLocationState.UNKNOWN,
    locationCount = locationCount,
)

interface LibraryRepository {
    fun loadPage(offset: Int, limit: Int): LibraryPage

    fun countTracks(): Int
}

interface LibraryCollectionRepository {
    fun loadCollections(type: LibraryCollectionType, offset: Int, limit: Int): LibraryCollectionPage

    fun countCollections(type: LibraryCollectionType): Int
}

interface FilteredLibraryRepository {
    fun loadFilteredPage(filter: LibraryTrackFilter, offset: Int, limit: Int): LibraryPage
    fun countFilteredTracks(filter: LibraryTrackFilter): Int
}

class RoomLibraryRepository(
    private val dao: LibraryDao,
) : LibraryRepository, LibraryCollectionRepository, FilteredLibraryRepository {
    override fun loadPage(offset: Int, limit: Int): LibraryPage {
        require(offset >= 0) { "offset must be non-negative" }
        require(limit > 0) { "limit must be positive" }
        val rows = dao.loadTracks(offset, limit + 1)
        return LibraryPage(
            songs = rows.take(limit).map(LibraryTrackRow::toLibrarySong),
            hasMore = rows.size > limit,
        )
    }

    override fun countTracks(): Int = dao.countTracks()

    override fun loadCollections(
        type: LibraryCollectionType,
        offset: Int,
        limit: Int,
    ): LibraryCollectionPage {
        require(offset >= 0) { "offset must be non-negative" }
        require(limit > 0) { "limit must be positive" }
        val rows = when (type) {
            LibraryCollectionType.ALBUMS -> dao.loadAlbums(offset, limit + 1)
            LibraryCollectionType.ARTISTS -> dao.loadArtists(offset, limit + 1)
            LibraryCollectionType.GENRES -> dao.loadGenres(offset, limit + 1)
            LibraryCollectionType.FOLDERS -> dao.loadFolders(offset, limit + 1)
        }
        return LibraryCollectionPage(
            collections = rows.take(limit),
            hasMore = rows.size > limit,
        )
    }

    override fun countCollections(type: LibraryCollectionType): Int = when (type) {
        LibraryCollectionType.ALBUMS -> dao.countAlbums()
        LibraryCollectionType.ARTISTS -> dao.countArtists()
        LibraryCollectionType.GENRES -> dao.countGenres()
        LibraryCollectionType.FOLDERS -> dao.countFolders()
    }

    override fun loadFilteredPage(filter: LibraryTrackFilter, offset: Int, limit: Int): LibraryPage {
        require(offset >= 0) { "offset must be non-negative" }
        require(limit > 0) { "limit must be positive" }
        val rows = dao.loadTracksRaw(SimpleSQLiteQuery(filteredTracksSql(filter, limit + 1, offset), filterArgs(filter)))
        return LibraryPage(rows.take(limit).map(LibraryTrackRow::toLibrarySong), rows.size > limit)
    }

    override fun countFilteredTracks(filter: LibraryTrackFilter): Int =
        dao.countTracksRaw(SimpleSQLiteQuery(filteredCountSql(filter), filterArgs(filter)))

    private fun filterArgs(filter: LibraryTrackFilter): Array<Any?> = when (filter) {
        is LibraryTrackFilter.Album -> arrayOf(filter.album, filter.albumArtist)
        is LibraryTrackFilter.Artist -> arrayOf(filter.artist)
        is LibraryTrackFilter.Genre -> arrayOf(filter.genre)
        is LibraryTrackFilter.Folder -> arrayOf(filter.path)
    }

    private fun filteredTracksSql(filter: LibraryTrackFilter, limit: Int, offset: Int): String =
        filteredSelectSql(filter) + " LIMIT $limit OFFSET $offset"

    private fun filteredCountSql(filter: LibraryTrackFilter): String =
        "SELECT COUNT(*) FROM tracks AS t LEFT JOIN track_metadata AS m ON m.trackId=t.track_id WHERE " + visibleTrackWhere(filter)

    private fun visibleTrackWhere(filter: LibraryTrackFilter): String {
        val criterion = when (filter) {
            is LibraryTrackFilter.Album -> "COALESCE((SELECT value FROM track_metadata_overrides WHERE trackId=t.track_id AND fieldName='ALBUM'), m.normalizedAlbum)=? AND COALESCE((SELECT value FROM track_metadata_overrides WHERE trackId=t.track_id AND fieldName='ALBUM_ARTIST'), m.normalizedAlbumArtist, (SELECT value FROM track_metadata_overrides WHERE trackId=t.track_id AND fieldName='ARTIST'), m.normalizedArtist, '')=?"
            is LibraryTrackFilter.Artist -> "COALESCE((SELECT value FROM track_metadata_overrides WHERE trackId=t.track_id AND fieldName='ARTIST'), m.normalizedArtist)=?"
            is LibraryTrackFilter.Genre -> "COALESCE((SELECT value FROM track_metadata_overrides WHERE trackId=t.track_id AND fieldName='GENRE'), m.normalizedGenres)=?"
            is LibraryTrackFilter.Folder -> "EXISTS (SELECT 1 FROM track_locations filter_l LEFT JOIN locator_bindings filter_b ON filter_b.location_id=filter_l.location_id LEFT JOIN catalog_exclusions filter_e ON filter_e.source_id=filter_l.source_id AND filter_e.locator=filter_b.locator WHERE filter_l.track_id=t.track_id AND filter_l.path=? AND filter_e.exclusion_id IS NULL)"
        }
        return "t.catalog_lifecycle='ACTIVE' AND ($criterion) AND (NOT EXISTS (SELECT 1 FROM track_locations any_l WHERE any_l.track_id=t.track_id) OR EXISTS (SELECT 1 FROM track_locations visible_l LEFT JOIN locator_bindings visible_b ON visible_b.location_id=visible_l.location_id LEFT JOIN catalog_exclusions visible_e ON visible_e.source_id=visible_l.source_id AND visible_e.locator=visible_b.locator WHERE visible_l.track_id=t.track_id AND visible_e.exclusion_id IS NULL))"
    }

    private fun filteredSelectSql(filter: LibraryTrackFilter): String {
        val where = visibleTrackWhere(filter)
        return """
            SELECT t.track_id AS trackId,
              COALESCE((SELECT value FROM track_metadata_overrides WHERE trackId=t.track_id AND fieldName='TITLE'),m.normalizedTitle) AS title,
              COALESCE((SELECT value FROM track_metadata_overrides WHERE trackId=t.track_id AND fieldName='ARTIST'),m.normalizedArtist) AS artist,
              COALESCE((SELECT value FROM track_metadata_overrides WHERE trackId=t.track_id AND fieldName='ALBUM'),m.normalizedAlbum) AS album,
              COALESCE(CAST((SELECT value FROM track_metadata_overrides WHERE trackId=t.track_id AND fieldName='DURATION_MS') AS INTEGER),m.normalizedDurationMs) AS durationMs,
              CASE WHEN m.trackId IS NULL THEN 0 ELSE 1 END AS metadataPresent,
              CASE WHEN (SELECT value FROM track_metadata_overrides WHERE trackId=t.track_id AND fieldName='TITLE') IS NOT NULL THEN 'AVAILABLE' ELSE m.normalizedTitleStatus END AS titleStatus,
              CASE WHEN (SELECT value FROM track_metadata_overrides WHERE trackId=t.track_id AND fieldName='ARTIST') IS NOT NULL THEN 'AVAILABLE' ELSE m.normalizedArtistStatus END AS artistStatus,
              CASE WHEN (SELECT value FROM track_metadata_overrides WHERE trackId=t.track_id AND fieldName='ALBUM') IS NOT NULL THEN 'AVAILABLE' ELSE m.normalizedAlbumStatus END AS albumStatus,
              CASE WHEN EXISTS (SELECT 1 FROM track_locations lc LEFT JOIN locator_bindings lb ON lb.location_id=lc.location_id LEFT JOIN catalog_exclusions le ON le.source_id=lc.source_id AND le.locator=lb.locator WHERE lc.track_id=t.track_id AND lc.availability='AVAILABLE' AND le.exclusion_id IS NULL) THEN 'AVAILABLE'
                   WHEN EXISTS (SELECT 1 FROM track_locations lc LEFT JOIN locator_bindings lb ON lb.location_id=lc.location_id LEFT JOIN catalog_exclusions le ON le.source_id=lc.source_id AND le.locator=lb.locator WHERE lc.track_id=t.track_id AND lc.availability='MISSING' AND le.exclusion_id IS NULL) THEN 'MISSING'
                   WHEN EXISTS (SELECT 1 FROM track_locations lc LEFT JOIN locator_bindings lb ON lb.location_id=lc.location_id LEFT JOIN catalog_exclusions le ON le.source_id=lc.source_id AND le.locator=lb.locator WHERE lc.track_id=t.track_id AND lc.availability='PERMISSION_LOST' AND le.exclusion_id IS NULL) THEN 'PERMISSION_LOST'
                   WHEN EXISTS (SELECT 1 FROM track_locations lc LEFT JOIN locator_bindings lb ON lb.location_id=lc.location_id LEFT JOIN catalog_exclusions le ON le.source_id=lc.source_id AND le.locator=lb.locator WHERE lc.track_id=t.track_id AND lc.availability='UNAVAILABLE' AND le.exclusion_id IS NULL) THEN 'UNAVAILABLE' ELSE 'UNKNOWN' END AS locationState,
              (SELECT COUNT(*) FROM track_locations lc LEFT JOIN locator_bindings lb ON lb.location_id=lc.location_id LEFT JOIN catalog_exclusions le ON le.source_id=lc.source_id AND le.locator=lb.locator WHERE lc.track_id=t.track_id AND le.exclusion_id IS NULL) AS locationCount
            FROM tracks t LEFT JOIN track_metadata m ON m.trackId=t.track_id
            WHERE $where
            ORDER BY CASE WHEN COALESCE((SELECT value FROM track_metadata_overrides WHERE trackId=t.track_id AND fieldName='TITLE'),m.normalizedTitle,'')='' THEN 1 ELSE 0 END,
              COALESCE((SELECT value FROM track_metadata_overrides WHERE trackId=t.track_id AND fieldName='TITLE'),m.normalizedTitle,'') COLLATE NOCASE,t.track_id
        """.trimIndent()
    }
}

data class LibraryUiState(
    val songs: List<LibrarySong> = emptyList(),
    val totalTracks: Int = 0,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val heading: String = "Your library",
)

class LibraryViewModel(
    private val pageLoader: (offset: Int, limit: Int) -> LibraryPage,
    private val totalLoader: () -> Int,
    private val filteredPageLoader: ((LibraryTrackFilter, Int, Int) -> LibraryPage)? = null,
    private val filteredTotalLoader: ((LibraryTrackFilter) -> Int)? = null,
) {
    val state: MutableState<LibraryUiState> = mutableStateOf(LibraryUiState())

    private var pageSize = DEFAULT_PAGE_SIZE
    private var nextOffset = 0
    private var currentFilter: LibraryTrackFilter? = null
    private var currentHeading = "Your library"

    fun loadInitial(pageSize: Int = DEFAULT_PAGE_SIZE) = loadInitial(null, "Your library", pageSize)

    fun loadInitial(filter: LibraryTrackFilter?, heading: String, pageSize: Int = DEFAULT_PAGE_SIZE) {
        if (state.value.loading) return
        this.pageSize = pageSize
        currentFilter = filter
        currentHeading = heading
        nextOffset = 0
        state.value = LibraryUiState(loading = true)
        runCatching {
            if (filter == null) pageLoader(0, pageSize) to totalLoader()
            else (filteredPageLoader ?: error("Filtered library is unavailable"))(filter, 0, pageSize) to
                (filteredTotalLoader ?: error("Filtered library count is unavailable"))(filter)
        }.onSuccess { (page, total) ->
            nextOffset = page.songs.size
            state.value = LibraryUiState(
                songs = page.songs,
                totalTracks = total,
                loaded = true,
                hasMore = page.hasMore,
                heading = currentHeading,
            )
        }.onFailure { error ->
            state.value = LibraryUiState(
                loaded = true,
                errorMessage = error.message ?: "Library could not be loaded",
                heading = currentHeading,
            )
        }
    }

    fun loadNextPage() {
        val current = state.value
        if (current.loading || !current.hasMore || !current.loaded) return
        state.value = current.copy(loading = true, errorMessage = null)
        runCatching {
            currentFilter?.let { (filteredPageLoader ?: error("Filtered library is unavailable"))(it, nextOffset, pageSize) }
                ?: pageLoader(nextOffset, pageSize)
        }
            .onSuccess { page ->
                nextOffset += page.songs.size
                state.value = state.value.copy(
                    songs = state.value.songs + page.songs,
                    loading = false,
                    hasMore = page.hasMore && page.songs.isNotEmpty(),
                )
            }.onFailure { error ->
                state.value = state.value.copy(
                    loading = false,
                    errorMessage = error.message ?: "More songs could not be loaded",
                )
            }
    }

    fun retry() {
        if (state.value.errorMessage == null || state.value.loading) return
        if (state.value.songs.isEmpty()) loadInitial(currentFilter, currentHeading, pageSize)
        else { state.value = state.value.copy(errorMessage = null); loadNextPage() }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }
}

data class LibraryCollectionUiState(
    val type: LibraryCollectionType = LibraryCollectionType.ALBUMS,
    val collections: List<LibraryCollectionRow> = emptyList(),
    val totalCollections: Int = 0,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
)

class LibraryCollectionViewModel(
    private val pageLoader: (type: LibraryCollectionType, offset: Int, limit: Int) -> LibraryCollectionPage,
    private val totalLoader: (type: LibraryCollectionType) -> Int,
) {
    val state: MutableState<LibraryCollectionUiState> = mutableStateOf(LibraryCollectionUiState())

    private var pageSize = LibraryViewModel.DEFAULT_PAGE_SIZE
    private var nextOffset = 0

    fun loadInitial(type: LibraryCollectionType, pageSize: Int = LibraryViewModel.DEFAULT_PAGE_SIZE) {
        if (state.value.loading) return
        this.pageSize = pageSize
        nextOffset = 0
        state.value = LibraryCollectionUiState(type = type, loading = true)
        runCatching {
            pageLoader(type, 0, pageSize) to totalLoader(type)
        }.onSuccess { (page, total) ->
            nextOffset = page.collections.size
            state.value = LibraryCollectionUiState(
                type = type,
                collections = page.collections,
                totalCollections = total,
                loaded = true,
                hasMore = page.hasMore,
            )
        }.onFailure { error ->
            state.value = LibraryCollectionUiState(
                type = type,
                loaded = true,
                errorMessage = error.message ?: "Collections could not be loaded",
            )
        }
    }

    fun loadNextPage() {
        val current = state.value
        if (current.loading || !current.hasMore || !current.loaded) return
        state.value = current.copy(loading = true, errorMessage = null)
        runCatching { pageLoader(current.type, nextOffset, pageSize) }
            .onSuccess { page ->
                nextOffset += page.collections.size
                state.value = state.value.copy(
                    collections = state.value.collections + page.collections,
                    loading = false,
                    hasMore = page.hasMore && page.collections.isNotEmpty(),
                )
            }.onFailure { error ->
                state.value = state.value.copy(
                    loading = false,
                    errorMessage = error.message ?: "More collections could not be loaded",
                )
            }
    }

    fun retry() {
        if (state.value.errorMessage == null || state.value.loading) return
        if (state.value.collections.isEmpty()) loadInitial(state.value.type, pageSize)
        else { state.value = state.value.copy(errorMessage = null); loadNextPage() }
    }
}
