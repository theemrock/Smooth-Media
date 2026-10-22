package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["normalized_name"], unique = true)],
)
data class PlaylistEntity(
    @PrimaryKey
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "playlist_entries",
    primaryKeys = ["playlist_id", "track_id"],
    indices = [Index(value = ["playlist_id", "position"]), Index(value = ["track_id"])],
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["playlist_id"], childColumns = ["playlist_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TrackEntity::class, parentColumns = ["track_id"], childColumns = ["track_id"], onDelete = ForeignKey.NO_ACTION),
    ],
)
data class PlaylistEntryEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    val position: Int,
    @ColumnInfo(name = "added_at_epoch_ms") val addedAtEpochMs: Long,
)
