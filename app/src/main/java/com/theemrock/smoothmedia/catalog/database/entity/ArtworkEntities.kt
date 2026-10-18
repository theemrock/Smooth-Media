package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "artwork_assets",
    indices = [Index("sourceId"), Index("availability")],
)
data class ArtworkAssetEntity(
    @androidx.room.PrimaryKey val assetId: String,
    val contentHash: String?,
    val sourceType: String,
    val sourceLocator: String,
    val sourceId: String?,
    val format: String?,
    val width: Int?,
    val height: Int?,
    val availability: String,
    val invalidatedAtEpochMs: Long?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "artwork_album_bindings",
    primaryKeys = ["collectionKey"],
    indices = [Index("assetId"), Index("sourceId")],
    foreignKeys = [
        ForeignKey(
            entity = ArtworkAssetEntity::class,
            parentColumns = ["assetId"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
)
data class ArtworkAlbumBindingEntity(
    val collectionKey: String,
    val assetId: String,
    val sourceId: String?,
    val sourcePriority: Int,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "artwork_overrides",
    primaryKeys = ["collectionKey"],
    indices = [Index("assetId")],
    foreignKeys = [
        ForeignKey(
            entity = ArtworkAssetEntity::class,
            parentColumns = ["assetId"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
)
data class ArtworkOverrideEntity(
    val collectionKey: String,
    val assetId: String,
    val updatedAtEpochMs: Long,
)
