package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "catalog_exclusions",
    foreignKeys = [
        ForeignKey(
            entity = LibrarySourceEntity::class,
            parentColumns = ["source_id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["source_id", "locator"], unique = true),
        Index("file_hash_digest"),
    ],
)
data class CatalogExclusionEntity(
    @PrimaryKey
    @ColumnInfo(name = "exclusion_id")
    val exclusionId: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    val locator: String,
    @Embedded(prefix = "file_hash_")
    val fileHash: StoredFileHash,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
)
