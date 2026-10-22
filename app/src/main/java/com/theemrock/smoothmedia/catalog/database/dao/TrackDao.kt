package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackLocationEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackWithLocations
import com.theemrock.smoothmedia.catalog.model.LocationAvailability

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertTrack(track: TrackEntity)

    @Query("SELECT * FROM tracks WHERE track_id = :trackId")
    fun findTrack(trackId: String): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertLocation(location: TrackLocationEntity)

    @Query("SELECT * FROM track_locations WHERE location_id = :locationId")
    fun findLocation(locationId: String): TrackLocationEntity?

    @Query("SELECT * FROM track_locations WHERE track_id = :trackId ORDER BY location_id")
    fun findLocations(trackId: String): List<TrackLocationEntity>

    @Query("SELECT track_id FROM tracks WHERE catalog_lifecycle = 'ACTIVE' AND (:afterTrackId IS NULL OR track_id > :afterTrackId) ORDER BY track_id LIMIT :limit")
    fun listActiveTrackIdsAfter(afterTrackId: String?, limit: Int): List<String>

    @Query("SELECT track_id FROM tracks WHERE catalog_lifecycle = 'ACTIVE' AND track_id IN (:trackIds)")
    fun listActiveTrackIds(trackIds: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM tracks WHERE catalog_lifecycle = 'ACTIVE'")
    fun countActiveTracks(): Int

    @Query("SELECT * FROM track_locations ORDER BY location_id")
    fun listLocations(): List<TrackLocationEntity>

    @Transaction
    @Query("SELECT * FROM tracks WHERE track_id = :trackId")
    fun findTrackWithLocations(trackId: String): TrackWithLocations?

    @Query(
        """
        UPDATE track_locations
        SET current_hash_algorithm_name = :replacementAlgorithmName,
            current_hash_algorithm_version = :replacementAlgorithmVersion,
            current_hash_digest = :replacementDigest,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE location_id = :locationId
          AND current_hash_algorithm_name = :expectedAlgorithmName
          AND current_hash_algorithm_version = :expectedAlgorithmVersion
          AND current_hash_digest = :expectedDigest
        """,
    )
    fun updateCurrentHashIfExpected(
        locationId: String,
        expectedAlgorithmName: String,
        expectedAlgorithmVersion: Int,
        expectedDigest: String,
        replacementAlgorithmName: String,
        replacementAlgorithmVersion: Int,
        replacementDigest: String,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE track_locations
        SET availability = :availability,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE location_id = :locationId
        """,
    )
    fun updateAvailability(
        locationId: String,
        availability: LocationAvailability,
        updatedAtEpochMs: Long,
    ): Int
}
