package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.theemrock.smoothmedia.catalog.database.entity.SmartPlaylistEntity

@Dao
interface SmartPlaylistDao {
    @Query("SELECT * FROM smart_playlists ORDER BY normalized_name")
    fun loadAll(): List<SmartPlaylistEntity>

    /** Aborts on duplicate normalized_name; the repository turns that into a user-facing error. */
    @Insert
    fun insert(entity: SmartPlaylistEntity)

    @Update
    fun update(entity: SmartPlaylistEntity): Int

    @Query("DELETE FROM smart_playlists WHERE smart_playlist_id = :smartPlaylistId")
    fun delete(smartPlaylistId: String): Int
}
