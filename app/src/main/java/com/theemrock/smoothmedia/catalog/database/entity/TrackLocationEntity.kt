package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.model.LocationAvailability

@Entity(
    tableName = "track_locations",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["track_id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = LibrarySourceEntity::class,
            parentColumns = ["source_id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index("track_id"),
        Index("source_id"),
        Index(value = ["location_id", "source_id"], unique = true),
        Index("original_hash_digest"),
        Index("current_hash_digest"),
    ],
)
data class TrackLocationEntity(
    @PrimaryKey
    @ColumnInfo(name = "location_id")
    val locationId: String,
    @ColumnInfo(name = "track_id")
    val trackId: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @Embedded(prefix = "original_hash_")
    val originalFileHash: StoredFileHash,
    @Embedded(prefix = "current_hash_")
    val currentFileHash: StoredFileHash,
    val format: AudioFormat,
    val availability: LocationAvailability,
    val path: String?,
    @ColumnInfo(name = "media_store_id")
    val mediaStoreId: Long?,
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,
    @ColumnInfo(name = "modified_at_epoch_ms")
    val modifiedAtEpochMs: Long,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long?,
    val bitrate: Int?,
    @ColumnInfo(name = "sample_rate_hz")
    val sampleRateHz: Int?,
    @ColumnInfo(name = "channel_count")
    val channelCount: Int?,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)

data class StoredFileHash(
    @ColumnInfo(name = "algorithm_name")
    val algorithmName: String,
    @ColumnInfo(name = "algorithm_version")
    val algorithmVersion: Int,
    val digest: String,
)
