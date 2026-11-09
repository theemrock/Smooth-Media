package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theemrock.smoothmedia.catalog.database.entity.EqOverrideEntity
import com.theemrock.smoothmedia.catalog.database.entity.EqProfileEntity

@Dao
interface EqDao {
    @Query("SELECT * FROM eq_profiles WHERE trackId = :trackId")
    fun findProfile(trackId: String): EqProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertProfile(value: EqProfileEntity)

    /** Active tracks with no profile from the given analyzer version; the scan queue. */
    @Query(
        """
        SELECT t.track_id FROM tracks AS t
        LEFT JOIN eq_profiles AS p ON p.trackId = t.track_id AND p.analyzerVersion = :analyzerVersion
        WHERE t.catalog_lifecycle = 'ACTIVE' AND p.trackId IS NULL
        ORDER BY t.track_id
        """,
    )
    fun trackIdsToScan(analyzerVersion: Int): List<String>

    /** Profiles that hold real data; empty blobs mark failed tracks. */
    @Query("SELECT COUNT(*) FROM eq_profiles WHERE analyzerVersion = :analyzerVersion AND length(windows) > 4")
    fun scannedCount(analyzerVersion: Int): Int

    @Query("SELECT COUNT(*) FROM tracks WHERE catalog_lifecycle = 'ACTIVE'")
    fun activeTrackCount(): Int

    /** Scanned active tracks with display title and artist; the caller sorts them. */
    @Query(
        """
        SELECT COALESCE(title_o.value, m.normalizedTitle, '') AS title,
               COALESCE(artist_o.value, m.normalizedArtist, '') AS artist,
               (SELECT b.locator FROM track_locations AS l
                JOIN locator_bindings AS b ON b.location_id = l.location_id
                WHERE l.track_id = t.track_id LIMIT 1) AS locator
        FROM eq_profiles AS p
        JOIN tracks AS t ON t.track_id = p.trackId
        LEFT JOIN track_metadata AS m ON m.trackId = t.track_id
        LEFT JOIN track_metadata_overrides AS title_o ON title_o.trackId = t.track_id AND title_o.fieldName = 'TITLE'
        LEFT JOIN track_metadata_overrides AS artist_o ON artist_o.trackId = t.track_id AND artist_o.fieldName = 'ARTIST'
        WHERE p.analyzerVersion = :analyzerVersion AND length(p.windows) > 4 AND t.catalog_lifecycle = 'ACTIVE'
        """,
    )
    fun scannedSongs(analyzerVersion: Int): List<EqScannedSong>

    @Query("SELECT * FROM eq_overrides WHERE trackId = :trackId")
    fun findOverride(trackId: String): EqOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertOverride(value: EqOverrideEntity)

    @Query("DELETE FROM eq_overrides WHERE trackId = :trackId")
    fun deleteOverride(trackId: String): Int
}

data class EqScannedSong(val title: String, val artist: String, val locator: String?)
