package com.theemrock.smoothmedia.library

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
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
) {
    /** Derived from persisted location state; no new catalog state is introduced. */
    val isAccessible: Boolean
        get() = locationState == LibraryLocationState.AVAILABLE

    val accessibilityDescription: String
        get() = when (locationState) {
            LibraryLocationState.AVAILABLE -> "Available"
            LibraryLocationState.MISSING -> "Unavailable: file missing"
            LibraryLocationState.UNAVAILABLE -> "Unavailable: source unavailable"
            LibraryLocationState.PERMISSION_LOST -> "Unavailable: permission lost"
            LibraryLocationState.UNKNOWN -> "Availability unknown"
        }
}

data class LibraryPage(
    val songs: List<LibrarySong>,
    val hasMore: Boolean,
)

data class LibraryDashboardData(
    val totalTracks: Int,
    val albumCount: Int,
    val artistCount: Int,
    val genreCount: Int,
    val folderCount: Int?,
    val songs: List<LibrarySong>,
    val albums: List<LibraryCollectionRow>,
    val artists: List<LibraryCollectionRow>,
    val genres: List<LibraryCollectionRow>,
)

internal fun dashboardFolderCount(realCount: Int?): Int? = realCount?.takeIf { it > 0 }

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
    val albumCount: Int = 0,
    val mp3Count: Int = 0,
    val flacCount: Int = 0,
)

val LibraryCollectionRow.artistStatsLine: String
    get() = "$albumCount albums · $trackCount songs"

val LibraryCollectionRow.artistFormatLabels: List<String>
    get() = buildList {
        if (mp3Count > 0) add("$mp3Count MP3")
        if (flacCount > 0) add("$flacCount FLAC")
    }

data class LibraryArtworkCandidate(
    val trackId: String,
    val locationId: String,
    val locator: String,
)

data class LibraryCollectionPage(
    val collections: List<LibraryCollectionRow>,
    val hasMore: Boolean,
)

enum class LibrarySearchCategory { SONGS, ARTISTS, ALBUMS, GENRES, FOLDERS }

data class LibrarySearchPage(
    val songs: List<LibrarySong> = emptyList(),
    val artists: List<LibraryCollectionRow> = emptyList(),
    val albums: List<LibraryCollectionRow> = emptyList(),
    val genres: List<LibraryCollectionRow> = emptyList(),
    val folders: List<LibraryCollectionRow> = emptyList(),
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty() && genres.isEmpty() && folders.isEmpty()
}

object LibrarySearchQuery {
    fun normalize(query: String): String? = query.trim().takeIf { it.isNotEmpty() }
    /** TRACKS_SQL and collection search use instr(), so the bound value is a literal needle. */
    fun likeArgument(query: String): String = query
    fun snapshotKey(query: String, songs: List<LibrarySong>): String =
        "search:${query.trim()}:${songs.joinToString(",") { it.trackId }}"
    fun tracksSql(): String = TRACKS_SQL

    const val TRACKS_SQL = """
        SELECT DISTINCT t.track_id AS trackId,
            COALESCE(title_override.value,m.normalizedTitle) AS title,
            COALESCE(artist_override.value,m.normalizedArtist) AS artist,
            COALESCE(album_override.value,m.normalizedAlbum) AS album,
            COALESCE(CAST(duration_override.value AS INTEGER),m.normalizedDurationMs) AS durationMs,
            CASE WHEN m.trackId IS NULL THEN 0 ELSE 1 END AS metadataPresent,
            CASE WHEN title_override.value IS NOT NULL THEN 'AVAILABLE' ELSE m.normalizedTitleStatus END AS titleStatus,
            CASE WHEN artist_override.value IS NOT NULL THEN 'AVAILABLE' ELSE m.normalizedArtistStatus END AS artistStatus,
            CASE WHEN album_override.value IS NOT NULL THEN 'AVAILABLE' ELSE m.normalizedAlbumStatus END AS albumStatus,
            CASE WHEN EXISTS (SELECT 1 FROM track_locations x LEFT JOIN locator_bindings xb ON xb.location_id=x.location_id LEFT JOIN catalog_exclusions xe ON xe.source_id=x.source_id AND xe.locator=xb.locator WHERE x.track_id=t.track_id AND x.availability='AVAILABLE' AND xe.exclusion_id IS NULL) THEN 'AVAILABLE'
                 WHEN EXISTS (SELECT 1 FROM track_locations x LEFT JOIN locator_bindings xb ON xb.location_id=x.location_id LEFT JOIN catalog_exclusions xe ON xe.source_id=x.source_id AND xe.locator=xb.locator WHERE x.track_id=t.track_id AND x.availability='MISSING' AND xe.exclusion_id IS NULL) THEN 'MISSING'
                 WHEN EXISTS (SELECT 1 FROM track_locations x LEFT JOIN locator_bindings xb ON xb.location_id=x.location_id LEFT JOIN catalog_exclusions xe ON xe.source_id=x.source_id AND xe.locator=xb.locator WHERE x.track_id=t.track_id AND x.availability='PERMISSION_LOST' AND xe.exclusion_id IS NULL) THEN 'PERMISSION_LOST'
                 ELSE 'UNAVAILABLE' END AS locationState,
            (SELECT COUNT(*) FROM track_locations x LEFT JOIN locator_bindings xb ON xb.location_id=x.location_id LEFT JOIN catalog_exclusions xe ON xe.source_id=x.source_id AND xe.locator=xb.locator WHERE x.track_id=t.track_id AND xe.exclusion_id IS NULL) AS locationCount
        FROM tracks t
        LEFT JOIN track_metadata m ON m.trackId=t.track_id
        LEFT JOIN track_metadata_overrides title_override ON title_override.trackId=t.track_id AND title_override.fieldName='TITLE'
        LEFT JOIN track_metadata_overrides artist_override ON artist_override.trackId=t.track_id AND artist_override.fieldName='ARTIST'
        LEFT JOIN track_metadata_overrides album_override ON album_override.trackId=t.track_id AND album_override.fieldName='ALBUM'
        LEFT JOIN track_metadata_overrides duration_override ON duration_override.trackId=t.track_id AND duration_override.fieldName='DURATION_MS'
        LEFT JOIN track_locations l ON l.track_id=t.track_id
        LEFT JOIN locator_bindings b ON b.location_id=l.location_id
        LEFT JOIN catalog_exclusions e ON e.source_id=l.source_id AND e.locator=b.locator
        WHERE t.catalog_lifecycle='ACTIVE' AND e.exclusion_id IS NULL AND
            (instr(lower(COALESCE(title_override.value,m.normalizedTitle,'')),lower(?))>0 OR instr(lower(COALESCE(artist_override.value,m.normalizedArtist,'')),lower(?))>0 OR instr(lower(COALESCE(album_override.value,m.normalizedAlbum,'')),lower(?))>0 OR instr(lower(COALESCE(m.normalizedGenres,'')),lower(?))>0 OR instr(lower(COALESCE(l.path,'')),lower(?))>0)
        ORDER BY CASE WHEN COALESCE(title_override.value,m.normalizedTitle,'')='' THEN 1 ELSE 0 END, COALESCE(title_override.value,m.normalizedTitle,'') COLLATE NOCASE, t.track_id
        LIMIT ? OFFSET ?
    """
}

interface LibrarySearchRepository {
    fun search(query: String, limit: Int = 50, offset: Int = 0): LibrarySearchPage
}

data class LibrarySearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val results: LibrarySearchPage = LibrarySearchPage(),
    val errorMessage: String? = null,
)

class LibrarySearchViewModel(
    private val repository: LibrarySearchRepository,
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
) {
    val state: MutableState<LibrarySearchUiState> = mutableStateOf(LibrarySearchUiState())
    private var pending: ScheduledFuture<*>? = null
    private var generation = 0L

    fun onQueryChanged(value: String) {
        generation += 1
        val requestGeneration = generation
        pending?.cancel(false)
        if (LibrarySearchQuery.normalize(value) == null) {
            state.value = LibrarySearchUiState(query = value)
            return
        }
        state.value = state.value.copy(query = value, loading = true, loaded = false, errorMessage = null)
        pending = scheduler.schedule({
            runCatching { repository.search(value) }
                .onSuccess { result ->
                    if (requestGeneration == generation) {
                        state.value = LibrarySearchUiState(query = value, loaded = true, results = result)
                    }
                }
                .onFailure { error ->
                    if (requestGeneration == generation) {
                        state.value = LibrarySearchUiState(query = value, loaded = true, errorMessage = error.message ?: "Search failed")
                    }
                }
        }, 250, TimeUnit.MILLISECONDS)
    }

    fun clear() = onQueryChanged("")

    fun close() {
        pending?.cancel(false)
        scheduler.shutdownNow()
    }
}

sealed interface LibraryTrackFilter {
    data class Album(
        val album: String,
        val albumArtist: String,
        val sourceId: String = AlbumGroupingKey.UNKNOWN_SOURCE,
        val folderPrefix: String = AlbumGroupingKey.UNKNOWN_FOLDER,
    ) : LibraryTrackFilter
    data class Artist(val artist: String) : LibraryTrackFilter
    data class Genre(val genre: String) : LibraryTrackFilter
    data class Folder(val path: String) : LibraryTrackFilter
}

data class AlbumGroupingInput(
    val album: String,
    val trackArtist: String,
    val albumArtist: String,
    val sourceId: String,
    val folderPrefix: String,
)

data class AlbumGroupingKey(
    val album: String,
    val albumArtist: String,
    val sourceId: String,
    val folderPrefix: String,
) {
    fun encoded(): String = listOf(album, albumArtist, sourceId, folderPrefix).joinToString(GROUP_SEPARATOR.toString())

    fun toFilter(): LibraryTrackFilter.Album =
        LibraryTrackFilter.Album(album, albumArtist, sourceId, folderPrefix)

    companion object {
        const val UNKNOWN_SOURCE = "NO_SOURCE"
        const val UNKNOWN_FOLDER = "NO_FOLDER"
        private const val GROUP_SEPARATOR = '\u001E'
        private const val LEGACY_SEPARATOR = '\u001F'

        fun decode(encoded: String, fallbackAlbum: String = "", fallbackAlbumArtist: String = ""): AlbumGroupingKey {
            val parts = encoded.split(GROUP_SEPARATOR)
            if (parts.size >= 4) {
                return AlbumGroupingKey(parts[0], parts[1], parts[2], parts.drop(3).joinToString(GROUP_SEPARATOR.toString()))
            }
            val legacy = encoded.split(LEGACY_SEPARATOR, limit = 2)
            return AlbumGroupingKey(
                album = legacy.getOrNull(0).orEmpty().ifBlank { fallbackAlbum },
                albumArtist = legacy.getOrNull(1).orEmpty().ifBlank { fallbackAlbumArtist },
                sourceId = UNKNOWN_SOURCE,
                folderPrefix = UNKNOWN_FOLDER,
            )
        }
    }
}

fun albumGroupingKey(input: AlbumGroupingInput): String = AlbumGroupingKey(
    album = input.album.trim(),
    albumArtist = input.albumArtist.trim(),
    sourceId = input.sourceId.trim().ifBlank { AlbumGroupingKey.UNKNOWN_SOURCE },
    folderPrefix = input.folderPrefix.trim().ifBlank { AlbumGroupingKey.UNKNOWN_FOLDER },
).encoded()

data class SuspiciousAlbumGrouping(
    val album: String,
    val sourceId: String,
    val folderPrefix: String,
    val trackArtists: Set<String>,
)

/**
 * Compilations without Album Artist are intentionally groupable, but remain
 * observable so callers can report the decision instead of silently merging it.
 */
fun findSuspiciousAlbumGroupings(inputs: List<AlbumGroupingInput>): List<SuspiciousAlbumGrouping> =
    inputs.groupBy {
        listOf(
            it.album.trim(),
            it.albumArtist.trim(),
            it.sourceId.trim().ifBlank { AlbumGroupingKey.UNKNOWN_SOURCE },
            it.folderPrefix.trim().ifBlank { AlbumGroupingKey.UNKNOWN_FOLDER },
        )
    }.mapNotNull { (group, rows) ->
        val artists = rows.map { it.trackArtist.trim() }.filter { it.isNotBlank() }.toSet()
        if (group[1].isBlank() && artists.size > 1) {
            SuspiciousAlbumGrouping(group[0], group[2], group[3], artists)
        } else {
            null
        }
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

interface LibraryArtworkCandidateRepository {
    fun loadAlbumArtworkCandidates(album: String, albumArtist: String, limit: Int): List<LibraryArtworkCandidate>
    fun loadTrackArtworkCandidates(trackId: String, limit: Int): List<LibraryArtworkCandidate>
}

interface FilteredLibraryRepository {
    fun loadFilteredPage(filter: LibraryTrackFilter, offset: Int, limit: Int): LibraryPage
    fun countFilteredTracks(filter: LibraryTrackFilter): Int
}

class RoomLibraryRepository(
    private val dao: LibraryDao,
) : LibraryRepository, LibraryCollectionRepository, FilteredLibraryRepository, LibraryArtworkCandidateRepository, LibrarySearchRepository {
    override fun search(query: String, limit: Int, offset: Int): LibrarySearchPage {
        val normalized = LibrarySearchQuery.normalize(query) ?: return LibrarySearchPage()
        require(limit > 0) { "limit must be positive" }
        require(offset >= 0) { "offset must be non-negative" }
        val arg = LibrarySearchQuery.likeArgument(normalized)
        val args = arrayOf<Any?>(arg, arg, arg, arg, arg, limit, offset)
        val categoryArgs = arrayOf<Any?>(arg, limit, offset)
        return LibrarySearchPage(
            songs = dao.searchTracksRaw(SimpleSQLiteQuery(LibrarySearchQuery.tracksSql(), args)).map(LibraryTrackRow::toLibrarySong),
            artists = dao.searchCollectionsRaw(SimpleSQLiteQuery(searchCollectionSql("artist"), categoryArgs)),
            albums = dao.searchCollectionsRaw(SimpleSQLiteQuery(searchCollectionSql("album"), categoryArgs)),
            genres = dao.searchCollectionsRaw(SimpleSQLiteQuery(searchCollectionSql("genre"), categoryArgs)),
            folders = dao.searchCollectionsRaw(SimpleSQLiteQuery(searchCollectionSql("path"), categoryArgs)),
        )
    }

    private fun searchCollectionSql(column: String): String = when (column) {
        "artist" -> "SELECT artist AS `key`, artist AS title, NULL AS subtitle, COUNT(DISTINCT trackId) AS trackCount, 0 AS albumCount, 0 AS mp3Count, 0 AS flacCount FROM (SELECT t.track_id AS trackId, COALESCE(o.value,m.normalizedArtist) AS artist FROM tracks t LEFT JOIN track_metadata m ON m.trackId=t.track_id LEFT JOIN track_metadata_overrides o ON o.trackId=t.track_id AND o.fieldName='ARTIST' WHERE t.catalog_lifecycle='ACTIVE') WHERE artist IS NOT NULL AND trim(artist)<>'' AND instr(lower(artist),lower(?))>0 GROUP BY artist ORDER BY artist COLLATE NOCASE LIMIT ? OFFSET ?"
        "album" -> """
            WITH RECURSIVE
            track_scope AS (
                SELECT t.track_id AS trackId,
                       COALESCE(MIN(l.source_id), 'NO_SOURCE') AS sourceId,
                       COALESCE(MIN(NULLIF(l.path, '')), MIN(b.locator), 'NO_FOLDER') AS locator
                FROM tracks t
                LEFT JOIN track_locations l ON l.track_id=t.track_id
                LEFT JOIN locator_bindings b ON b.location_id=l.location_id
                GROUP BY t.track_id
            ),
            scope_chars(trackId, sourceId, locator, position, lastSlash) AS (
                SELECT trackId, sourceId, locator, 1, 0 FROM track_scope
                UNION ALL
                SELECT trackId, sourceId, locator, position+1,
                       CASE WHEN substr(locator, position, 1)='/' OR substr(locator, position, 3)='%2F' THEN position ELSE lastSlash END
                FROM scope_chars WHERE position <= length(locator)
            ),
            scopes AS (
                SELECT trackId, sourceId,
                       CASE WHEN MAX(lastSlash)>0 THEN substr(locator,1,MAX(lastSlash)) ELSE locator END AS folderPrefix
                FROM scope_chars GROUP BY trackId, sourceId, locator
            ),
            albums AS (
                SELECT t.track_id AS trackId,
                       COALESCE(album_override.value,m.normalizedAlbum) AS album,
                       COALESCE(album_artist_override.value,m.normalizedAlbumArtist,'') AS albumArtist,
                       COALESCE(scopes.sourceId,'NO_SOURCE') AS sourceId,
                       COALESCE(scopes.folderPrefix,'NO_FOLDER') AS folderPrefix
                FROM tracks t
                LEFT JOIN track_metadata m ON m.trackId=t.track_id
                LEFT JOIN track_metadata_overrides album_override ON album_override.trackId=t.track_id AND album_override.fieldName='ALBUM'
                LEFT JOIN track_metadata_overrides album_artist_override ON album_artist_override.trackId=t.track_id AND album_artist_override.fieldName='ALBUM_ARTIST'
                LEFT JOIN scopes ON scopes.trackId=t.track_id
                WHERE t.catalog_lifecycle='ACTIVE'
            )
            SELECT album || char(31) || albumArtist || char(30) || sourceId || char(30) || folderPrefix AS `key`,
                   album AS title, NULLIF(albumArtist,'') AS subtitle,
                   COUNT(DISTINCT trackId) AS trackCount, 0 AS albumCount, 0 AS mp3Count, 0 AS flacCount
            FROM albums
            WHERE album IS NOT NULL AND trim(album)<>'' AND instr(lower(album),lower(?))>0
            GROUP BY album, albumArtist, sourceId, folderPrefix
            ORDER BY album COLLATE NOCASE, albumArtist COLLATE NOCASE, sourceId, folderPrefix
            LIMIT ? OFFSET ?
        """.trimIndent()
        "genre" -> "SELECT genre AS `key`, genre AS title, NULL AS subtitle, COUNT(DISTINCT trackId) AS trackCount, 0 AS albumCount, 0 AS mp3Count, 0 AS flacCount FROM (SELECT t.track_id AS trackId, COALESCE(o.value,m.normalizedGenres) AS genre FROM tracks t LEFT JOIN track_metadata m ON m.trackId=t.track_id LEFT JOIN track_metadata_overrides o ON o.trackId=t.track_id AND o.fieldName='GENRE' WHERE t.catalog_lifecycle='ACTIVE') WHERE genre IS NOT NULL AND trim(genre)<>'' AND instr(lower(genre),lower(?))>0 GROUP BY genre ORDER BY genre COLLATE NOCASE LIMIT ? OFFSET ?"
        else -> "SELECT path AS `key`, path AS title, NULL AS subtitle, COUNT(DISTINCT trackId) AS trackCount, 0 AS albumCount, 0 AS mp3Count, 0 AS flacCount FROM (SELECT t.track_id AS trackId, l.path AS path FROM tracks t JOIN track_locations l ON l.track_id=t.track_id WHERE t.catalog_lifecycle='ACTIVE') WHERE path IS NOT NULL AND trim(path)<>'' AND instr(lower(path),lower(?))>0 GROUP BY path ORDER BY path COLLATE NOCASE LIMIT ? OFFSET ?"
    }
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

    fun loadDashboard(limit: Int = 5): LibraryDashboardData {
        require(limit > 0) { "limit must be positive" }
        return LibraryDashboardData(
            totalTracks = countTracks(),
            albumCount = countCollections(LibraryCollectionType.ALBUMS),
            artistCount = countCollections(LibraryCollectionType.ARTISTS),
            genreCount = countCollections(LibraryCollectionType.GENRES),
            folderCount = dashboardFolderCount(countCollections(LibraryCollectionType.FOLDERS)),
            songs = loadPage(0, limit).songs,
            albums = loadCollections(LibraryCollectionType.ALBUMS, 0, limit).collections,
            artists = loadCollections(LibraryCollectionType.ARTISTS, 0, limit).collections,
            genres = loadCollections(LibraryCollectionType.GENRES, 0, limit).collections,
        )
    }

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

    override fun loadAlbumArtworkCandidates(
        album: String,
        albumArtist: String,
        limit: Int,
    ): List<LibraryArtworkCandidate> = dao.loadAlbumArtworkCandidates(album, albumArtist, limit)
        .map { LibraryArtworkCandidate(it.trackId, it.locationId, it.locator) }

    override fun loadTrackArtworkCandidates(
        trackId: String,
        limit: Int,
    ): List<LibraryArtworkCandidate> = dao.loadTrackArtworkCandidates(trackId, limit)
        .map { LibraryArtworkCandidate(it.trackId, it.locationId, it.locator) }

    override fun loadFilteredPage(filter: LibraryTrackFilter, offset: Int, limit: Int): LibraryPage {
        require(offset >= 0) { "offset must be non-negative" }
        require(limit > 0) { "limit must be positive" }
        val rows = dao.loadTracksRaw(SimpleSQLiteQuery(filteredTracksSql(filter, limit + 1, offset), filterArgs(filter)))
        return LibraryPage(rows.take(limit).map(LibraryTrackRow::toLibrarySong), rows.size > limit)
    }

    override fun countFilteredTracks(filter: LibraryTrackFilter): Int =
        dao.countTracksRaw(SimpleSQLiteQuery(filteredCountSql(filter), filterArgs(filter)))

    private fun filterArgs(filter: LibraryTrackFilter): Array<Any?> = when (filter) {
        is LibraryTrackFilter.Album -> arrayOf(
            filter.album,
            filter.albumArtist,
            filter.sourceId,
            filter.folderPrefix,
            filter.sourceId,
            filter.folderPrefix,
        )
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
            is LibraryTrackFilter.Album -> "COALESCE((SELECT value FROM track_metadata_overrides WHERE trackId=t.track_id AND fieldName='ALBUM'), m.normalizedAlbum)=? AND COALESCE((SELECT value FROM track_metadata_overrides WHERE trackId=t.track_id AND fieldName='ALBUM_ARTIST'), m.normalizedAlbumArtist, '')=? AND ( (?='${AlbumGroupingKey.UNKNOWN_SOURCE}' AND ?='${AlbumGroupingKey.UNKNOWN_FOLDER}' AND NOT EXISTS (SELECT 1 FROM track_locations scope_l WHERE scope_l.track_id=t.track_id)) OR EXISTS (SELECT 1 FROM track_locations scope_l LEFT JOIN locator_bindings scope_b ON scope_b.location_id=scope_l.location_id WHERE scope_l.track_id=t.track_id AND COALESCE(scope_l.source_id,'${AlbumGroupingKey.UNKNOWN_SOURCE}')=? AND COALESCE(NULLIF(scope_l.path,''),scope_b.locator,'${AlbumGroupingKey.UNKNOWN_FOLDER}') LIKE ? || '%') )"
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
    val isFiltered: Boolean = false,
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
    private var savedScrollIndex = 0
    private var savedScrollOffset = 0

    val scrollIndex: Int get() = savedScrollIndex
    val scrollOffset: Int get() = savedScrollOffset

    fun saveScrollPosition(index: Int, offset: Int) {
        savedScrollIndex = index.coerceAtLeast(0)
        savedScrollOffset = offset.coerceAtLeast(0)
    }

    fun loadInitial(pageSize: Int = DEFAULT_PAGE_SIZE) = loadInitial(null, "Your library", pageSize)

    fun loadInitial(filter: LibraryTrackFilter?, heading: String, pageSize: Int = DEFAULT_PAGE_SIZE) {
        if (state.value.loading) return
        savedScrollIndex = 0
        savedScrollOffset = 0
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
                isFiltered = filter != null,
            )
        }.onFailure { error ->
            state.value = LibraryUiState(
                loaded = true,
                errorMessage = error.message ?: "Library could not be loaded",
                heading = currentHeading,
                isFiltered = filter != null,
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

    /** Loads the current source query with its existing filter and deterministic ordering. */
    fun snapshotCurrentList(): List<LibrarySong> {
        val result = mutableListOf<LibrarySong>()
        var offset = 0
        do {
            val page = currentFilter?.let { filter ->
                (filteredPageLoader ?: error("Filtered library is unavailable"))(filter, offset, pageSize)
            } ?: pageLoader(offset, pageSize)
            result += page.songs
            offset += page.songs.size
            if (page.songs.isEmpty() || !page.hasMore) break
        } while (true)
        return result
    }

    fun currentSnapshotKey(): String = buildString {
        append(currentHeading)
        currentFilter?.let { append(":").append(it) }
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
