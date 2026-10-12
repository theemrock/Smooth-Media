package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    val status: ScanSessionStatus,
    @ColumnInfo(name = "started_at_epoch_ms")
    val startedAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
    @ColumnInfo(name = "completed_at_epoch_ms")
    val completedAtEpochMs: Long?,
)

@Entity(
    tableName = "active_scan_leases",
    foreignKeys = [
        ForeignKey(
            entity = ScanSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index(value = ["session_id"], unique = true)],
)
data class ActiveScanLeaseEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "slot_id")
    val slotId: Int,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
)

@Entity(
    tableName = "source_scan_checkpoints",
    primaryKeys = ["session_id", "source_id"],
    foreignKeys = [
        ForeignKey(
            entity = ScanSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
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
    indices = [Index("source_id")],
)
data class SourceScanCheckpointEntity(
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "continuation_token")
    val continuationToken: String?,
    @ColumnInfo(name = "discovered_count")
    val discoveredCount: Int,
    @ColumnInfo(name = "processed_count")
    val processedCount: Int,
    val coverage: ScanCheckpointCoverage,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "scan_candidates",
    primaryKeys = ["session_id", "source_id", "candidate_key"],
    foreignKeys = [
        ForeignKey(
            entity = ScanSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
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
        ForeignKey(
            entity = TrackLocationEntity::class,
            parentColumns = ["location_id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index("source_id"),
        Index("location_id"),
        Index("state"),
    ],
)
data class ScanCandidateEntity(
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "candidate_key")
    val candidateKey: String,
    val state: ScanCandidateState,
    @ColumnInfo(name = "location_id")
    val locationId: String?,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)

enum class ScanSessionStatus {
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED,
}

enum class ScanCheckpointCoverage {
    PARTIAL,
    COMPLETE,
}

enum class ScanCandidateState {
    DISCOVERED,
    PROCESSING,
    COMPLETED,
    SKIPPED,
}
