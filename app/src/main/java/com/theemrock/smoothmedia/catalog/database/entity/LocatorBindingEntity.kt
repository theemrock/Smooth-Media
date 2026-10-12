package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "locator_bindings",
    foreignKeys = [
        ForeignKey(
            entity = TrackLocationEntity::class,
            parentColumns = ["location_id", "source_id"],
            childColumns = ["location_id", "source_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["location_id", "source_id"]),
        Index(value = ["location_id"], unique = true),
        Index("source_id"),
    ],
)
data class LocatorBindingEntity(
    @PrimaryKey
    val locator: String,
    @ColumnInfo(name = "location_id")
    val locationId: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "bound_at_epoch_ms")
    val boundAtEpochMs: Long,
)
