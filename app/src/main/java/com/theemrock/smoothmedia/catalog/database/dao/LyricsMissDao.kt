package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theemrock.smoothmedia.catalog.database.entity.LyricsMissEntity

@Dao
interface LyricsMissDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(value: LyricsMissEntity)

    @Query("DELETE FROM lyrics_misses WHERE trackId = :trackId")
    fun delete(trackId: String): Int
}
