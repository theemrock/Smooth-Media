package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_metadata",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["track_id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TrackMetadataEntity(
    @PrimaryKey
    val trackId: String,
    val rawTitle: String?,
    val rawArtist: String?,
    val rawAlbum: String?,
    val rawAlbumArtist: String?,
    val rawGenre: String?,
    val rawTrackNumber: String?,
    val rawDiscNumber: String?,
    val rawYear: String?,
    val rawDurationMs: String?,
    val normalizedTitle: String?,
    val normalizedTitleStatus: String,
    val normalizedArtist: String?,
    val normalizedArtistStatus: String,
    val normalizedAlbum: String?,
    val normalizedAlbumStatus: String,
    val normalizedAlbumArtist: String?,
    val normalizedAlbumArtistStatus: String,
    val normalizedGenres: String?,
    val normalizedGenresStatus: String,
    val normalizedTrackNumber: Int?,
    val normalizedTrackNumberStatus: String,
    val normalizedDiscNumber: Int?,
    val normalizedDiscNumberStatus: String,
    val normalizedYear: Int?,
    val normalizedYearStatus: String,
    val normalizedDurationMs: Long?,
    val normalizedDurationMsStatus: String,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "track_metadata_overrides",
    primaryKeys = ["trackId", "fieldName"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["track_id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TrackMetadataOverrideEntity(
    val trackId: String,
    val fieldName: String,
    val value: String,
    val updatedAtEpochMs: Long,
)
