package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.theemrock.smoothmedia.library.LibraryCollectionRow
import com.theemrock.smoothmedia.library.LibraryArtworkCandidate
import com.theemrock.smoothmedia.library.LibraryTrackRow

@Dao
interface LibraryDao {
    @RawQuery
    fun searchTracksRaw(query: SupportSQLiteQuery): List<LibraryTrackRow>

    @RawQuery
    fun searchCollectionsRaw(query: SupportSQLiteQuery): List<LibraryCollectionRow>

    @RawQuery
    fun loadTracksRaw(query: SupportSQLiteQuery): List<LibraryTrackRow>

    @RawQuery
    fun countTracksRaw(query: SupportSQLiteQuery): Int
    @Query(
        """
        SELECT
            t.track_id AS trackId,
            COALESCE(title_override.value, m.normalizedTitle) AS title,
            COALESCE(artist_override.value, m.normalizedArtist) AS artist,
            COALESCE(album_override.value, m.normalizedAlbum) AS album,
            COALESCE(
                CAST(duration_override.value AS INTEGER),
                m.normalizedDurationMs
            ) AS durationMs,
            CASE WHEN m.trackId IS NULL THEN 0 ELSE 1 END AS metadataPresent,
            CASE WHEN title_override.value IS NOT NULL THEN 'AVAILABLE'
                 ELSE m.normalizedTitleStatus END AS titleStatus,
            CASE WHEN artist_override.value IS NOT NULL THEN 'AVAILABLE'
                 ELSE m.normalizedArtistStatus END AS artistStatus,
            CASE WHEN album_override.value IS NOT NULL THEN 'AVAILABLE'
                 ELSE m.normalizedAlbumStatus END AS albumStatus,
            CASE
                WHEN EXISTS(
                    SELECT 1 FROM track_locations available_l
                    LEFT JOIN locator_bindings available_b
                        ON available_b.location_id = available_l.location_id
                    LEFT JOIN catalog_exclusions available_e
                        ON available_e.source_id = available_l.source_id
                       AND available_e.locator = available_b.locator
                    WHERE available_l.track_id = t.track_id
                      AND available_e.exclusion_id IS NULL
                      AND available_l.availability = 'AVAILABLE'
                ) THEN 'AVAILABLE'
                WHEN EXISTS(
                    SELECT 1 FROM track_locations missing_l
                    LEFT JOIN locator_bindings missing_b
                        ON missing_b.location_id = missing_l.location_id
                    LEFT JOIN catalog_exclusions missing_e
                        ON missing_e.source_id = missing_l.source_id
                       AND missing_e.locator = missing_b.locator
                    WHERE missing_l.track_id = t.track_id
                      AND missing_e.exclusion_id IS NULL
                      AND missing_l.availability = 'MISSING'
                ) THEN 'MISSING'
                WHEN EXISTS(
                    SELECT 1 FROM track_locations permission_l
                    LEFT JOIN locator_bindings permission_b
                        ON permission_b.location_id = permission_l.location_id
                    LEFT JOIN catalog_exclusions permission_e
                        ON permission_e.source_id = permission_l.source_id
                       AND permission_e.locator = permission_b.locator
                    WHERE permission_l.track_id = t.track_id
                      AND permission_e.exclusion_id IS NULL
                      AND permission_l.availability = 'PERMISSION_LOST'
                ) THEN 'PERMISSION_LOST'
                WHEN EXISTS(
                    SELECT 1 FROM track_locations unavailable_l
                    LEFT JOIN locator_bindings unavailable_b
                        ON unavailable_b.location_id = unavailable_l.location_id
                    LEFT JOIN catalog_exclusions unavailable_e
                        ON unavailable_e.source_id = unavailable_l.source_id
                       AND unavailable_e.locator = unavailable_b.locator
                    WHERE unavailable_l.track_id = t.track_id
                      AND unavailable_e.exclusion_id IS NULL
                      AND unavailable_l.availability = 'UNAVAILABLE'
                ) THEN 'UNAVAILABLE'
                ELSE 'UNKNOWN'
            END AS locationState,
            CAST((
                SELECT COUNT(*)
                FROM track_locations visible_l
                LEFT JOIN locator_bindings visible_b
                    ON visible_b.location_id = visible_l.location_id
                LEFT JOIN catalog_exclusions visible_e
                    ON visible_e.source_id = visible_l.source_id
                   AND visible_e.locator = visible_b.locator
                WHERE visible_l.track_id = t.track_id
                  AND visible_e.exclusion_id IS NULL
            ) AS INTEGER) AS locationCount
        FROM tracks AS t
        LEFT JOIN track_metadata AS m ON m.trackId = t.track_id
        LEFT JOIN track_metadata_overrides AS title_override
            ON title_override.trackId = t.track_id AND title_override.fieldName = 'TITLE'
        LEFT JOIN track_metadata_overrides AS artist_override
            ON artist_override.trackId = t.track_id AND artist_override.fieldName = 'ARTIST'
        LEFT JOIN track_metadata_overrides AS album_override
            ON album_override.trackId = t.track_id AND album_override.fieldName = 'ALBUM'
        LEFT JOIN track_metadata_overrides AS duration_override
            ON duration_override.trackId = t.track_id AND duration_override.fieldName = 'DURATION_MS'
        WHERE t.catalog_lifecycle = 'ACTIVE'
          AND (
              NOT EXISTS (
                  SELECT 1 FROM track_locations any_l
                  WHERE any_l.track_id = t.track_id
              )
              OR EXISTS (
                  SELECT 1 FROM track_locations visible_l
                  LEFT JOIN locator_bindings visible_b
                      ON visible_b.location_id = visible_l.location_id
                  LEFT JOIN catalog_exclusions visible_e
                      ON visible_e.source_id = visible_l.source_id
                     AND visible_e.locator = visible_b.locator
                  WHERE visible_l.track_id = t.track_id
                    AND visible_e.exclusion_id IS NULL
              )
          )
        ORDER BY CASE
                     WHEN COALESCE(title_override.value, m.normalizedTitle, '') = '' THEN 1
                     ELSE 0
                 END,
                 COALESCE(title_override.value, m.normalizedTitle, '') COLLATE NOCASE,
                 t.track_id
        LIMIT :limit OFFSET :offset
        """,
    )
    fun loadTracks(offset: Int, limit: Int): List<LibraryTrackRow>

    @Query(
        """
        SELECT COUNT(*)
        FROM tracks AS t
        WHERE t.catalog_lifecycle = 'ACTIVE'
          AND (
              NOT EXISTS (
                  SELECT 1 FROM track_locations any_l
                  WHERE any_l.track_id = t.track_id
              )
              OR EXISTS (
                  SELECT 1 FROM track_locations visible_l
                  LEFT JOIN locator_bindings visible_b
                      ON visible_b.location_id = visible_l.location_id
                  LEFT JOIN catalog_exclusions visible_e
                      ON visible_e.source_id = visible_l.source_id
                     AND visible_e.locator = visible_b.locator
                  WHERE visible_l.track_id = t.track_id
                    AND visible_e.exclusion_id IS NULL
              )
          )
        """,
    )
    fun countTracks(): Int

    @Query(
        """
        SELECT
            album || char(31) || albumArtist AS `key`,
            album AS title,
            NULLIF(albumArtist, '') AS subtitle,
            COUNT(DISTINCT trackId) AS trackCount,
            0 AS albumCount,
            0 AS mp3Count,
            0 AS flacCount
        FROM (
            SELECT
                t.track_id AS trackId,
                COALESCE(album_override.value, m.normalizedAlbum) AS album,
                COALESCE(
                    album_artist_override.value,
                    m.normalizedAlbumArtist,
                    artist_override.value,
                    m.normalizedArtist,
                    ''
                ) AS albumArtist
            FROM tracks AS t
            LEFT JOIN track_metadata AS m ON m.trackId = t.track_id
            LEFT JOIN track_metadata_overrides AS artist_override
                ON artist_override.trackId = t.track_id AND artist_override.fieldName = 'ARTIST'
            LEFT JOIN track_metadata_overrides AS album_override
                ON album_override.trackId = t.track_id AND album_override.fieldName = 'ALBUM'
            LEFT JOIN track_metadata_overrides AS album_artist_override
                ON album_artist_override.trackId = t.track_id AND album_artist_override.fieldName = 'ALBUM_ARTIST'
            WHERE t.catalog_lifecycle = 'ACTIVE'
              AND (
                  NOT EXISTS (SELECT 1 FROM track_locations any_l WHERE any_l.track_id = t.track_id)
                  OR EXISTS (
                      SELECT 1
                      FROM track_locations visible_l
                      LEFT JOIN locator_bindings visible_b ON visible_b.location_id = visible_l.location_id
                      LEFT JOIN catalog_exclusions visible_e
                          ON visible_e.source_id = visible_l.source_id
                         AND visible_e.locator = visible_b.locator
                      WHERE visible_l.track_id = t.track_id
                        AND visible_e.exclusion_id IS NULL
                  )
              )
        )
        WHERE album IS NOT NULL AND trim(album) <> ''
        GROUP BY album, albumArtist
        ORDER BY album COLLATE NOCASE, albumArtist COLLATE NOCASE, `key`
        LIMIT :limit OFFSET :offset
        """,
    )
    fun loadAlbums(offset: Int, limit: Int): List<LibraryCollectionRow>

    @Query(
        """
        SELECT t.track_id AS trackId, l.location_id AS locationId, b.locator AS locator
        FROM tracks AS t
        JOIN track_locations AS l ON l.track_id = t.track_id
        JOIN locator_bindings AS b ON b.location_id = l.location_id
        LEFT JOIN catalog_exclusions AS e
            ON e.source_id = l.source_id AND e.locator = b.locator
        LEFT JOIN track_metadata AS m ON m.trackId = t.track_id
        LEFT JOIN track_metadata_overrides AS artist_override
            ON artist_override.trackId = t.track_id AND artist_override.fieldName = 'ARTIST'
        LEFT JOIN track_metadata_overrides AS album_override
            ON album_override.trackId = t.track_id AND album_override.fieldName = 'ALBUM'
        LEFT JOIN track_metadata_overrides AS album_artist_override
            ON album_artist_override.trackId = t.track_id AND album_artist_override.fieldName = 'ALBUM_ARTIST'
        WHERE t.catalog_lifecycle = 'ACTIVE'
          AND l.availability = 'AVAILABLE'
          AND e.exclusion_id IS NULL
          AND COALESCE(album_override.value, m.normalizedAlbum) = :album
          AND COALESCE(
              album_artist_override.value,
              m.normalizedAlbumArtist,
              artist_override.value,
              m.normalizedArtist,
              ''
          ) = :albumArtist
        ORDER BY t.track_id, l.location_id
        LIMIT :limit
        """,
    )
    fun loadAlbumArtworkCandidates(album: String, albumArtist: String, limit: Int): List<LibraryArtworkCandidate>

    @Query(
        """
        SELECT t.track_id AS trackId, l.location_id AS locationId, b.locator AS locator
        FROM tracks AS t
        JOIN track_locations AS l ON l.track_id = t.track_id
        JOIN locator_bindings AS b ON b.location_id = l.location_id
        LEFT JOIN catalog_exclusions AS e
            ON e.source_id = l.source_id AND e.locator = b.locator
        WHERE t.track_id = :trackId
          AND t.catalog_lifecycle = 'ACTIVE'
          AND l.availability = 'AVAILABLE'
          AND e.exclusion_id IS NULL
        ORDER BY t.track_id, l.location_id
        LIMIT :limit
        """,
    )
    fun loadTrackArtworkCandidates(trackId: String, limit: Int): List<LibraryArtworkCandidate>

    @Query(
        """
        SELECT
            artist AS `key`,
            artist AS title,
            NULL AS subtitle,
            COUNT(DISTINCT trackId) AS trackCount,
            COUNT(DISTINCT CASE WHEN album IS NOT NULL AND trim(album) <> '' THEN album END) AS albumCount,
            COUNT(DISTINCT CASE WHEN hasMp3 = 1 THEN trackId END) AS mp3Count,
            COUNT(DISTINCT CASE WHEN hasFlac = 1 THEN trackId END) AS flacCount
        FROM (
            SELECT
                t.track_id AS trackId,
                COALESCE(artist_override.value, m.normalizedArtist) AS artist,
                COALESCE(album_override.value, m.normalizedAlbum) AS album,
                MAX(CASE WHEN l.format = 'MP3' AND e.exclusion_id IS NULL THEN 1 ELSE 0 END) AS hasMp3,
                MAX(CASE WHEN l.format = 'FLAC' AND e.exclusion_id IS NULL THEN 1 ELSE 0 END) AS hasFlac
            FROM tracks AS t
            LEFT JOIN track_metadata AS m ON m.trackId = t.track_id
            LEFT JOIN track_metadata_overrides AS artist_override
                ON artist_override.trackId = t.track_id AND artist_override.fieldName = 'ARTIST'
            LEFT JOIN track_metadata_overrides AS album_override
                ON album_override.trackId = t.track_id AND album_override.fieldName = 'ALBUM'
            LEFT JOIN track_locations AS l ON l.track_id = t.track_id
            LEFT JOIN locator_bindings AS b ON b.location_id = l.location_id
            LEFT JOIN catalog_exclusions AS e
                ON e.source_id = l.source_id AND e.locator = b.locator
            WHERE t.catalog_lifecycle = 'ACTIVE'
              AND (
                  NOT EXISTS (SELECT 1 FROM track_locations any_l WHERE any_l.track_id = t.track_id)
                  OR EXISTS (
                      SELECT 1
                      FROM track_locations visible_l
                      LEFT JOIN locator_bindings visible_b ON visible_b.location_id = visible_l.location_id
                      LEFT JOIN catalog_exclusions visible_e
                          ON visible_e.source_id = visible_l.source_id
                         AND visible_e.locator = visible_b.locator
                      WHERE visible_l.track_id = t.track_id
                        AND visible_e.exclusion_id IS NULL
                  )
              )
            GROUP BY t.track_id, artist, album
        )
        WHERE artist IS NOT NULL AND trim(artist) <> ''
        GROUP BY artist
        ORDER BY artist COLLATE NOCASE, `key`
        LIMIT :limit OFFSET :offset
        """,
    )
    fun loadArtists(offset: Int, limit: Int): List<LibraryCollectionRow>

    @Query(
        """
        SELECT
            genre AS `key`,
            genre AS title,
            NULL AS subtitle,
            COUNT(DISTINCT trackId) AS trackCount,
            0 AS albumCount,
            0 AS mp3Count,
            0 AS flacCount
        FROM (
            SELECT
                t.track_id AS trackId,
                COALESCE(genre_override.value, m.normalizedGenres) AS genre
            FROM tracks AS t
            LEFT JOIN track_metadata AS m ON m.trackId = t.track_id
            LEFT JOIN track_metadata_overrides AS genre_override
                ON genre_override.trackId = t.track_id AND genre_override.fieldName = 'GENRE'
            WHERE t.catalog_lifecycle = 'ACTIVE'
              AND (
                  NOT EXISTS (SELECT 1 FROM track_locations any_l WHERE any_l.track_id = t.track_id)
                  OR EXISTS (
                      SELECT 1
                      FROM track_locations visible_l
                      LEFT JOIN locator_bindings visible_b ON visible_b.location_id = visible_l.location_id
                      LEFT JOIN catalog_exclusions visible_e
                          ON visible_e.source_id = visible_l.source_id
                         AND visible_e.locator = visible_b.locator
                      WHERE visible_l.track_id = t.track_id
                        AND visible_e.exclusion_id IS NULL
                  )
              )
        )
        WHERE genre IS NOT NULL AND trim(genre) <> ''
        GROUP BY genre
        ORDER BY genre COLLATE NOCASE, `key`
        LIMIT :limit OFFSET :offset
        """,
    )
    fun loadGenres(offset: Int, limit: Int): List<LibraryCollectionRow>

    @Query(
        """
        SELECT
            path AS `key`,
            path AS title,
            NULL AS subtitle,
            COUNT(DISTINCT trackId) AS trackCount,
            0 AS albumCount,
            0 AS mp3Count,
            0 AS flacCount
        FROM (
            SELECT
                t.track_id AS trackId,
                l.path AS path
            FROM tracks AS t
            JOIN track_locations AS l ON l.track_id = t.track_id
            LEFT JOIN locator_bindings AS b ON b.location_id = l.location_id
            LEFT JOIN catalog_exclusions AS e
                ON e.source_id = l.source_id AND e.locator = b.locator
            WHERE t.catalog_lifecycle = 'ACTIVE'
              AND e.exclusion_id IS NULL
              AND l.path IS NOT NULL
              AND trim(l.path) <> ''
        )
        GROUP BY path
        ORDER BY path COLLATE NOCASE, `key`
        LIMIT :limit OFFSET :offset
        """,
    )
    fun loadFolders(offset: Int, limit: Int): List<LibraryCollectionRow>

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT album, albumArtist
            FROM (
                SELECT
                    COALESCE(album_override.value, m.normalizedAlbum) AS album,
                    COALESCE(album_artist_override.value, m.normalizedAlbumArtist, artist_override.value, m.normalizedArtist, '') AS albumArtist
                FROM tracks AS t
                LEFT JOIN track_metadata AS m ON m.trackId = t.track_id
                LEFT JOIN track_metadata_overrides AS artist_override ON artist_override.trackId = t.track_id AND artist_override.fieldName = 'ARTIST'
                LEFT JOIN track_metadata_overrides AS album_override ON album_override.trackId = t.track_id AND album_override.fieldName = 'ALBUM'
                LEFT JOIN track_metadata_overrides AS album_artist_override ON album_artist_override.trackId = t.track_id AND album_artist_override.fieldName = 'ALBUM_ARTIST'
                WHERE t.catalog_lifecycle = 'ACTIVE'
                  AND (NOT EXISTS (SELECT 1 FROM track_locations any_l WHERE any_l.track_id = t.track_id) OR EXISTS (SELECT 1 FROM track_locations visible_l LEFT JOIN locator_bindings visible_b ON visible_b.location_id = visible_l.location_id LEFT JOIN catalog_exclusions visible_e ON visible_e.source_id = visible_l.source_id AND visible_e.locator = visible_b.locator WHERE visible_l.track_id = t.track_id AND visible_e.exclusion_id IS NULL))
            ) WHERE album IS NOT NULL AND trim(album) <> ''
            GROUP BY album, albumArtist
        )
        """,
    )
    fun countAlbums(): Int

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT COALESCE(artist_override.value, m.normalizedArtist) AS artist
            FROM tracks AS t
            LEFT JOIN track_metadata AS m ON m.trackId = t.track_id
            LEFT JOIN track_metadata_overrides AS artist_override ON artist_override.trackId = t.track_id AND artist_override.fieldName = 'ARTIST'
            WHERE t.catalog_lifecycle = 'ACTIVE'
              AND (NOT EXISTS (SELECT 1 FROM track_locations any_l WHERE any_l.track_id = t.track_id) OR EXISTS (SELECT 1 FROM track_locations visible_l LEFT JOIN locator_bindings visible_b ON visible_b.location_id = visible_l.location_id LEFT JOIN catalog_exclusions visible_e ON visible_e.source_id = visible_l.source_id AND visible_e.locator = visible_b.locator WHERE visible_l.track_id = t.track_id AND visible_e.exclusion_id IS NULL))
              AND COALESCE(artist_override.value, m.normalizedArtist) IS NOT NULL
              AND trim(COALESCE(artist_override.value, m.normalizedArtist)) <> ''
            GROUP BY artist
        )
        """,
    )
    fun countArtists(): Int

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT COALESCE(genre_override.value, m.normalizedGenres) AS genre
            FROM tracks AS t
            LEFT JOIN track_metadata AS m ON m.trackId = t.track_id
            LEFT JOIN track_metadata_overrides AS genre_override ON genre_override.trackId = t.track_id AND genre_override.fieldName = 'GENRE'
            WHERE t.catalog_lifecycle = 'ACTIVE'
              AND (NOT EXISTS (SELECT 1 FROM track_locations any_l WHERE any_l.track_id = t.track_id) OR EXISTS (SELECT 1 FROM track_locations visible_l LEFT JOIN locator_bindings visible_b ON visible_b.location_id = visible_l.location_id LEFT JOIN catalog_exclusions visible_e ON visible_e.source_id = visible_l.source_id AND visible_e.locator = visible_b.locator WHERE visible_l.track_id = t.track_id AND visible_e.exclusion_id IS NULL))
              AND COALESCE(genre_override.value, m.normalizedGenres) IS NOT NULL
              AND trim(COALESCE(genre_override.value, m.normalizedGenres)) <> ''
            GROUP BY genre
        )
        """,
    )
    fun countGenres(): Int

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT l.path
            FROM tracks AS t
            JOIN track_locations AS l ON l.track_id = t.track_id
            LEFT JOIN locator_bindings AS b ON b.location_id = l.location_id
            LEFT JOIN catalog_exclusions AS e ON e.source_id = l.source_id AND e.locator = b.locator
            WHERE t.catalog_lifecycle = 'ACTIVE' AND e.exclusion_id IS NULL AND l.path IS NOT NULL AND trim(l.path) <> ''
            GROUP BY l.path
        )
        """,
    )
    fun countFolders(): Int

}
