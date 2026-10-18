package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "metadata_apply_sessions",
    foreignKeys = [ForeignKey(entity = MetadataRecoverySessionEntity::class, parentColumns = ["session_id"], childColumns = ["auditSessionId"], onDelete = ForeignKey.NO_ACTION)],
    indices = [Index("auditSessionId"), Index("status")],
)
data class MetadataApplySessionEntity(
    @PrimaryKey val sessionId: String,
    val auditSessionId: String,
    val status: String,
    val cursorDecisionKey: String?,
    val totalItems: Int,
    val processedItems: Int,
    val successCount: Int,
    val skippedStaleCount: Int,
    val failedCount: Int,
    val startedAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
)

@Entity(
    tableName = "metadata_apply_items",
    primaryKeys = ["applySessionId", "decisionSessionId", "trackId", "fieldName"],
    foreignKeys = [ForeignKey(entity = MetadataApplySessionEntity::class, parentColumns = ["sessionId"], childColumns = ["applySessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["applySessionId", "state"]), Index("trackId")],
)
data class MetadataApplyItemEntity(
    val applySessionId: String,
    val decisionSessionId: String,
    val trackId: String,
    val fieldName: String,
    val sourceId: String,
    val locationId: String,
    val expectedOldValue: String?,
    val newValue: String?,
    val evidenceType: String,
    val state: String,
    val errorMessage: String?,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "metadata_apply_change_log",
    foreignKeys = [ForeignKey(entity = MetadataApplySessionEntity::class, parentColumns = ["sessionId"], childColumns = ["applySessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("applySessionId"), Index(value = ["trackId", "fieldName"])],
)
data class MetadataApplyChangeLogEntity(
    @PrimaryKey(autoGenerate = true) val changeId: Long = 0,
    val applySessionId: String,
    val trackId: String,
    val fieldName: String,
    val oldValue: String?,
    val newValue: String?,
    val evidenceType: String,
    val decisionKey: String,
    val occurredAtEpochMs: Long,
    val result: String,
)

@Entity(
    tableName = "metadata_apply_leases",
    foreignKeys = [ForeignKey(entity = MetadataApplySessionEntity::class, parentColumns = ["sessionId"], childColumns = ["applySessionId"], onDelete = ForeignKey.CASCADE)],
)
data class MetadataApplyLeaseEntity(
    @PrimaryKey val applySessionId: String,
    val owner: String,
    val expiresAtEpochMs: Long,
    val heartbeatAtEpochMs: Long,
)
