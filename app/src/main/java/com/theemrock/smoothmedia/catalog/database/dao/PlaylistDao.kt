package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theemrock.smoothmedia.catalog.database.entity.PlaylistEntity
import com.theemrock.smoothmedia.catalog.database.entity.PlaylistEntryEntity

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY normalized_name, playlist_id")
    fun listAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE playlist_id = :playlistId")
    fun find(playlistId: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE normalized_name = :normalizedName")
    fun findByNormalizedName(normalizedName: String): PlaylistEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_entries WHERE playlist_id = :playlistId")
    fun maxPosition(playlistId: String): Int

    @Query("SELECT * FROM playlist_entries WHERE playlist_id = :playlistId ORDER BY position, track_id")
    fun listEntries(playlistId: String): List<PlaylistEntryEntity>

    @Query("SELECT track_id FROM playlist_entries WHERE playlist_id = :playlistId AND track_id IN (:trackIds)")
    fun existingTrackIds(playlistId: String, trackIds: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertEntries(entries: List<PlaylistEntryEntity>): List<Long>
}
