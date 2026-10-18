package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theemrock.smoothmedia.catalog.database.entity.TrackMetadataEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackMetadataOverrideEntity

@Dao
interface TrackMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(metadata: TrackMetadataEntity)

    @Query(
        """
        UPDATE track_metadata
        SET normalizedTitle = :newTitle,
            normalizedTitleStatus = 'AVAILABLE',
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE trackId = :trackId
          AND ((:oldTitle IS NULL AND normalizedTitle IS NULL) OR normalizedTitle = :oldTitle)
          AND EXISTS (
            SELECT 1 FROM tracks t
            WHERE t.track_id = :trackId
              AND t.catalog_lifecycle = 'ACTIVE'
          )
          AND EXISTS (
            SELECT 1
            FROM track_locations l
            JOIN locator_bindings b
              ON b.location_id = l.location_id AND b.source_id = l.source_id
            JOIN library_sources s
              ON s.source_id = l.source_id
            WHERE l.track_id = :trackId
              AND l.location_id = :locationId
              AND l.source_id = :sourceId
              AND l.availability = 'AVAILABLE'
              AND s.availability = 'AVAILABLE'
              AND s.permission_state IN ('GRANTED', 'NOT_REQUIRED')
              AND s.selection_state = 'ACTIVE'
          )
          AND NOT EXISTS (
            SELECT 1 FROM track_metadata_overrides o
            WHERE o.trackId = :trackId AND o.fieldName = 'TITLE'
          )
        """,
    )
    fun updateReviewedTitle(
        trackId: String,
        sourceId: String,
        locationId: String,
        oldTitle: String?,
        newTitle: String,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE track_metadata
        SET normalizedArtist = :newValue,
            normalizedArtistStatus = 'AVAILABLE',
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE trackId = :trackId
          AND ((:oldValue IS NULL AND normalizedArtist IS NULL) OR normalizedArtist = :oldValue)
          AND EXISTS (SELECT 1 FROM tracks t WHERE t.track_id = :trackId AND t.catalog_lifecycle = 'ACTIVE')
          AND EXISTS (
            SELECT 1 FROM track_locations l
            JOIN locator_bindings b ON b.location_id = l.location_id AND b.source_id = l.source_id
            JOIN library_sources s ON s.source_id = l.source_id
            WHERE l.track_id = :trackId AND l.location_id = :locationId AND l.source_id = :sourceId
              AND l.availability = 'AVAILABLE' AND s.availability = 'AVAILABLE'
              AND s.permission_state IN ('GRANTED', 'NOT_REQUIRED') AND s.selection_state = 'ACTIVE'
          )
          AND NOT EXISTS (SELECT 1 FROM track_metadata_overrides o WHERE o.trackId = :trackId AND o.fieldName = 'ARTIST')
        """,
    )
    fun updateReviewedArtist(trackId: String, sourceId: String, locationId: String, oldValue: String?, newValue: String, updatedAtEpochMs: Long): Int

    @Query(
        """
        UPDATE track_metadata
        SET normalizedAlbum = :newValue,
            normalizedAlbumStatus = 'AVAILABLE',
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE trackId = :trackId
          AND ((:oldValue IS NULL AND normalizedAlbum IS NULL) OR normalizedAlbum = :oldValue)
          AND EXISTS (SELECT 1 FROM tracks t WHERE t.track_id = :trackId AND t.catalog_lifecycle = 'ACTIVE')
          AND EXISTS (
            SELECT 1 FROM track_locations l
            JOIN locator_bindings b ON b.location_id = l.location_id AND b.source_id = l.source_id
            JOIN library_sources s ON s.source_id = l.source_id
            WHERE l.track_id = :trackId AND l.location_id = :locationId AND l.source_id = :sourceId
              AND l.availability = 'AVAILABLE' AND s.availability = 'AVAILABLE'
              AND s.permission_state IN ('GRANTED', 'NOT_REQUIRED') AND s.selection_state = 'ACTIVE'
          )
          AND NOT EXISTS (SELECT 1 FROM track_metadata_overrides o WHERE o.trackId = :trackId AND o.fieldName = 'ALBUM')
        """,
    )
    fun updateReviewedAlbum(trackId: String, sourceId: String, locationId: String, oldValue: String?, newValue: String, updatedAtEpochMs: Long): Int

    @Query(
        """
        UPDATE track_metadata
        SET normalizedAlbumArtist = :newValue,
            normalizedAlbumArtistStatus = 'AVAILABLE',
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE trackId = :trackId
          AND ((:oldValue IS NULL AND normalizedAlbumArtist IS NULL) OR normalizedAlbumArtist = :oldValue)
          AND EXISTS (SELECT 1 FROM tracks t WHERE t.track_id = :trackId AND t.catalog_lifecycle = 'ACTIVE')
          AND EXISTS (
            SELECT 1 FROM track_locations l
            JOIN locator_bindings b ON b.location_id = l.location_id AND b.source_id = l.source_id
            JOIN library_sources s ON s.source_id = l.source_id
            WHERE l.track_id = :trackId AND l.location_id = :locationId AND l.source_id = :sourceId
              AND l.availability = 'AVAILABLE' AND s.availability = 'AVAILABLE'
              AND s.permission_state IN ('GRANTED', 'NOT_REQUIRED') AND s.selection_state = 'ACTIVE'
          )
          AND NOT EXISTS (SELECT 1 FROM track_metadata_overrides o WHERE o.trackId = :trackId AND o.fieldName = 'ALBUM_ARTIST')
        """,
    )
    fun updateReviewedAlbumArtist(trackId: String, sourceId: String, locationId: String, oldValue: String?, newValue: String, updatedAtEpochMs: Long): Int

    @Query(
        """
        UPDATE track_metadata
        SET normalizedGenres = :newValue,
            normalizedGenresStatus = 'AVAILABLE',
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE trackId = :trackId
          AND ((:oldValue IS NULL AND normalizedGenres IS NULL) OR normalizedGenres = :oldValue)
          AND EXISTS (SELECT 1 FROM tracks t WHERE t.track_id = :trackId AND t.catalog_lifecycle = 'ACTIVE')
          AND EXISTS (
            SELECT 1 FROM track_locations l
            JOIN locator_bindings b ON b.location_id = l.location_id AND b.source_id = l.source_id
            JOIN library_sources s ON s.source_id = l.source_id
            WHERE l.track_id = :trackId AND l.location_id = :locationId AND l.source_id = :sourceId
              AND l.availability = 'AVAILABLE' AND s.availability = 'AVAILABLE'
              AND s.permission_state IN ('GRANTED', 'NOT_REQUIRED') AND s.selection_state = 'ACTIVE'
          )
          AND NOT EXISTS (SELECT 1 FROM track_metadata_overrides o WHERE o.trackId = :trackId AND o.fieldName = 'GENRE')
        """,
    )
    fun updateReviewedGenre(trackId: String, sourceId: String, locationId: String, oldValue: String?, newValue: String, updatedAtEpochMs: Long): Int

    @Query("SELECT * FROM track_metadata WHERE trackId = :trackId")
    fun find(trackId: String): TrackMetadataEntity?

    @Query("SELECT * FROM track_metadata ORDER BY trackId")
    fun listAll(): List<TrackMetadataEntity>

    @Query(
        "SELECT track_id FROM tracks WHERE catalog_lifecycle = 'ACTIVE' ORDER BY track_id LIMIT :limit",
    )
    fun listActiveTrackIds(limit: Int): List<String>

    @Query(
        """
        SELECT t.track_id
        FROM tracks AS t
        JOIN track_metadata AS m ON m.trackId = t.track_id
        WHERE t.catalog_lifecycle = 'ACTIVE'
          AND (
            m.normalizedTitle LIKE '%�%' OR m.normalizedArtist LIKE '%�%' OR
            m.normalizedAlbum LIKE '%�%' OR m.normalizedAlbumArtist LIKE '%�%' OR
            m.normalizedGenres LIKE '%�%'
          )
        ORDER BY t.track_id
        LIMIT :limit
        """,
    )
    fun listActiveTrackIdsWithReplacementCharacters(limit: Int): List<String>

    @Query(
        """
        SELECT t.track_id
        FROM tracks AS t
        JOIN track_metadata AS m ON m.trackId = t.track_id
        WHERE t.catalog_lifecycle = 'ACTIVE'
          AND (
            instr(COALESCE(m.normalizedTitle, ''), char(65533)) > 0 OR
            instr(COALESCE(m.normalizedArtist, ''), char(65533)) > 0 OR
            instr(COALESCE(m.normalizedAlbum, ''), char(65533)) > 0 OR
            instr(COALESCE(m.normalizedAlbumArtist, ''), char(65533)) > 0 OR
            instr(COALESCE(m.normalizedGenres, ''), char(65533)) > 0 OR
            instr(COALESCE(m.normalizedTitle, ''), char(195)) > 0 OR
            instr(COALESCE(m.normalizedArtist, ''), char(195)) > 0 OR
            instr(COALESCE(m.normalizedAlbum, ''), char(195)) > 0 OR
            instr(COALESCE(m.normalizedAlbumArtist, ''), char(195)) > 0 OR
            instr(COALESCE(m.normalizedGenres, ''), char(195)) > 0 OR
            instr(COALESCE(m.normalizedTitle, ''), char(196)) > 0 OR
            instr(COALESCE(m.normalizedArtist, ''), char(196)) > 0 OR
            instr(COALESCE(m.normalizedAlbum, ''), char(196)) > 0 OR
            instr(COALESCE(m.normalizedAlbumArtist, ''), char(196)) > 0 OR
            instr(COALESCE(m.normalizedGenres, ''), char(196)) > 0 OR
            instr(COALESCE(m.normalizedTitle, ''), char(197)) > 0 OR
            instr(COALESCE(m.normalizedArtist, ''), char(197)) > 0 OR
            instr(COALESCE(m.normalizedAlbum, ''), char(197)) > 0 OR
            instr(COALESCE(m.normalizedAlbumArtist, ''), char(197)) > 0 OR
            instr(COALESCE(m.normalizedGenres, ''), char(197)) > 0 OR
            instr(COALESCE(m.normalizedTitle, ''), char(194)) > 0 OR
            instr(COALESCE(m.normalizedArtist, ''), char(194)) > 0 OR
            instr(COALESCE(m.normalizedAlbum, ''), char(194)) > 0 OR
            instr(COALESCE(m.normalizedAlbumArtist, ''), char(194)) > 0 OR
            instr(COALESCE(m.normalizedGenres, ''), char(194)) > 0 OR
            instr(COALESCE(m.normalizedTitle, ''), char(226)) > 0 OR
            instr(COALESCE(m.normalizedArtist, ''), char(226)) > 0 OR
            instr(COALESCE(m.normalizedAlbum, ''), char(226)) > 0 OR
            instr(COALESCE(m.normalizedAlbumArtist, ''), char(226)) > 0 OR
            instr(COALESCE(m.normalizedGenres, ''), char(226)) > 0 OR
            instr(COALESCE(m.normalizedTitle, ''), char(240)) > 0 OR
            instr(COALESCE(m.normalizedArtist, ''), char(240)) > 0 OR
            instr(COALESCE(m.normalizedAlbum, ''), char(240)) > 0 OR
            instr(COALESCE(m.normalizedAlbumArtist, ''), char(240)) > 0 OR
            instr(COALESCE(m.normalizedGenres, ''), char(240)) > 0
          )
        ORDER BY t.track_id
        LIMIT :limit
        """,
    )
    fun listActiveTrackIdsWithMojibakeMarkers(limit: Int): List<String>

    @Query(
        """
        SELECT t.track_id
        FROM tracks AS t
        LEFT JOIN track_metadata AS m ON m.trackId = t.track_id
        WHERE m.trackId IS NULL
          AND (:afterTrackId IS NULL OR t.track_id > :afterTrackId)
        ORDER BY t.track_id
        LIMIT :limit
        """,
    )
    fun listTrackIdsMissingMetadata(afterTrackId: String?, limit: Int): List<String>

    @Query(
        """
        SELECT COUNT(*)
        FROM tracks AS t
        LEFT JOIN track_metadata AS m ON m.trackId = t.track_id
        WHERE m.trackId IS NULL
        """,
    )
    fun countTracksMissingMetadata(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertOverride(override: TrackMetadataOverrideEntity)

    @Query("SELECT * FROM track_metadata_overrides WHERE trackId = :trackId")
    fun listOverrides(trackId: String): List<TrackMetadataOverrideEntity>

    @Query("SELECT * FROM track_metadata_overrides WHERE trackId IN (:trackIds) ORDER BY trackId, fieldName")
    fun listOverridesForTracks(trackIds: List<String>): List<TrackMetadataOverrideEntity>

    @Query(
        "DELETE FROM track_metadata_overrides " +
            "WHERE trackId = :trackId AND fieldName = :fieldName",
    )
    fun clearOverride(trackId: String, fieldName: String): Int
}
