package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/** Scanned band levels for one track; [windows] is an EqProfileCodec blob. */
@Entity(
    tableName = "eq_profiles",
    primaryKeys = ["trackId"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["track_id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class EqProfileEntity(
    val trackId: String,
    val analyzerVersion: Int,
    val fileSignature: String,
    val windows: ByteArray,
    val updatedAtEpochMs: Long,
)

/** User's manual EQ for one track; [levelsMb] is 24 comma-separated millibel values. */
@Entity(
    tableName = "eq_overrides",
    primaryKeys = ["trackId"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["track_id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EqOverrideEntity(
    val trackId: String,
    val levelsMb: String,
    val updatedAtEpochMs: Long,
)
