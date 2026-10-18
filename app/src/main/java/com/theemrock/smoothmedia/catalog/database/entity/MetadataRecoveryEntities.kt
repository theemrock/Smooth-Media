package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

enum class MetadataRecoverySessionStatus {
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED,
}

@Entity(tableName = "metadata_recovery_sessions", indices = [Index("status")])
data class MetadataRecoverySessionEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    val status: MetadataRecoverySessionStatus,
    @ColumnInfo(name = "cursor_track_id")
    val cursorTrackId: String?,
    @ColumnInfo(name = "total_tracks")
    val totalTracks: Int,
    @ColumnInfo(name = "processed_tracks")
    val processedTracks: Int,
    @ColumnInfo(name = "auto_repair_count")
    val autoRepairCount: Int,
    @ColumnInfo(name = "no_change_count")
    val noChangeCount: Int,
    @ColumnInfo(name = "review_required_count")
    val reviewRequiredCount: Int,
    @ColumnInfo(name = "unrecoverable_count")
    val unrecoverableCount: Int,
    @ColumnInfo(name = "read_error_count")
    val readErrorCount: Int,
    @ColumnInfo(name = "lease_owner")
    val leaseOwner: String?,
    @ColumnInfo(name = "lease_expires_at_epoch_ms")
    val leaseExpiresAtEpochMs: Long?,
    @ColumnInfo(name = "started_at_epoch_ms")
    val startedAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
    @ColumnInfo(name = "completed_at_epoch_ms")
    val completedAtEpochMs: Long?,
)

@Entity(
    tableName = "metadata_recovery_decisions",
    primaryKeys = ["session_id", "track_id", "field_name"],
    foreignKeys = [
        ForeignKey(
            entity = MetadataRecoverySessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["track_id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("track_id"), Index("decision")],
)
data class MetadataRecoveryDecisionEntity(
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "track_id")
    val trackId: String,
    @ColumnInfo(name = "field_name")
    val fieldName: String,
    val decision: String,
    @ColumnInfo(name = "evidence_type")
    val evidenceType: String,
    @ColumnInfo(name = "old_value")
    val oldValue: String?,
    @ColumnInfo(name = "new_value")
    val newValue: String?,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
)
