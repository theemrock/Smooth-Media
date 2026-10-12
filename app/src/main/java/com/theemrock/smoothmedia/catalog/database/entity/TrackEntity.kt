package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey
    @ColumnInfo(name = "track_id")
    val trackId: String,
    @ColumnInfo(name = "catalog_lifecycle")
    val catalogLifecycle: CatalogLifecycle,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)

enum class CatalogLifecycle {
    ACTIVE,
    TRASHED,
}

data class TrackWithLocations(
    @Embedded
    val track: TrackEntity,
    @Relation(
        parentColumn = "track_id",
        entityColumn = "track_id",
    )
    val locations: List<TrackLocationEntity>,
)
