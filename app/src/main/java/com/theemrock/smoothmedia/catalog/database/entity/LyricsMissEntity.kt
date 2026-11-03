package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * A track whose lyrics lookup did not yield lyrics. [kind] says why:
 * NOT_FOUND (no record), INSTRUMENTAL (record without words), ERROR (transient failure).
 * Bulk fetch never repeats NOT_FOUND or INSTRUMENTAL on its own; the user re-runs them on purpose.
 */
@Entity(
    tableName = "lyrics_misses",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["track_id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LyricsMissEntity(
    @PrimaryKey
    @ColumnInfo(name = "trackId")
    val trackId: String,
    @ColumnInfo(name = "checkedAtEpochMs")
    val checkedAtEpochMs: Long,
    @ColumnInfo(name = "kind", defaultValue = "'NOT_FOUND'")
    val kind: String = "NOT_FOUND",
)
