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

    @Query("SELECT * FROM eq_overrides WHERE trackId = :trackId")
    fun findOverride(trackId: String): EqOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertOverride(value: EqOverrideEntity)

    @Query("DELETE FROM eq_overrides WHERE trackId = :trackId")
    fun deleteOverride(trackId: String): Int
}
