package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "smart_playlists",
    indices = [Index(value = ["normalized_name"], unique = true)],
)
data class SmartPlaylistEntity(
    @PrimaryKey
    @ColumnInfo(name = "smart_playlist_id") val smartPlaylistId: String,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    /** Text form written by SmartRuleCodec; decoded on read. */
    @ColumnInfo(name = "rule_text") val ruleText: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
)
