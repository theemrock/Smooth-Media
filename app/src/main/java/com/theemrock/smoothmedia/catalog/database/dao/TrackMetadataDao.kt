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

    @Query("SELECT * FROM track_metadata WHERE trackId = :trackId")
    fun find(trackId: String): TrackMetadataEntity?

    @Query("SELECT * FROM track_metadata ORDER BY trackId")
    fun listAll(): List<TrackMetadataEntity>

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
