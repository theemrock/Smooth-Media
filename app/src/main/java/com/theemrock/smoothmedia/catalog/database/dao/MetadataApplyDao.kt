package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theemrock.smoothmedia.catalog.database.entity.MetadataApplyChangeLogEntity
import com.theemrock.smoothmedia.catalog.database.entity.MetadataApplyItemEntity
import com.theemrock.smoothmedia.catalog.database.entity.MetadataApplyLeaseEntity
import com.theemrock.smoothmedia.catalog.database.entity.MetadataApplySessionEntity

@Dao
interface MetadataApplyDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSession(session: MetadataApplySessionEntity)

    @Query("SELECT * FROM metadata_apply_sessions WHERE sessionId = :sessionId")
    fun findSession(sessionId: String): MetadataApplySessionEntity?

    @Query("SELECT * FROM metadata_apply_sessions WHERE auditSessionId = :auditSessionId AND status = 'DRY_RUN' ORDER BY startedAtEpochMs DESC LIMIT 1")
    fun findPendingDryRun(auditSessionId: String): MetadataApplySessionEntity?

    @Query("SELECT * FROM metadata_apply_sessions WHERE auditSessionId = :auditSessionId AND status = 'COMPLETED' ORDER BY completedAtEpochMs DESC LIMIT 1")
    fun findCompletedForAudit(auditSessionId: String): MetadataApplySessionEntity?

    @Query("UPDATE metadata_apply_sessions SET status = :status, cursorDecisionKey = :cursor, processedItems = processedItems + :processed, successCount = successCount + :success, skippedStaleCount = skippedStaleCount + :stale, failedCount = failedCount + :failed, updatedAtEpochMs = :now, completedAtEpochMs = :completedAt WHERE sessionId = :sessionId")
    fun updateSessionProgress(sessionId: String, status: String, cursor: String?, processed: Int, success: Int, stale: Int, failed: Int, now: Long, completedAt: Long?): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertItems(items: List<MetadataApplyItemEntity>)

    @Query("SELECT * FROM metadata_apply_items WHERE applySessionId = :sessionId AND state = 'PENDING' ORDER BY decisionSessionId, trackId, fieldName LIMIT :limit")
    fun pendingItems(sessionId: String, limit: Int): List<MetadataApplyItemEntity>

    @Query("UPDATE metadata_apply_items SET state = :state, errorMessage = :errorMessage, updatedAtEpochMs = :now WHERE applySessionId = :sessionId AND decisionSessionId = :decisionSessionId AND trackId = :trackId AND fieldName = :fieldName AND state = 'PENDING'")
    fun markItem(sessionId: String, decisionSessionId: String, trackId: String, fieldName: String, state: String, errorMessage: String?, now: Long): Int

    @Insert
    fun insertChangeLog(entry: MetadataApplyChangeLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertLease(lease: MetadataApplyLeaseEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertLeaseIfAbsent(lease: MetadataApplyLeaseEntity): Long

    @Query("UPDATE metadata_apply_leases SET owner = :owner, expiresAtEpochMs = :expiresAt, heartbeatAtEpochMs = :now WHERE applySessionId = :sessionId AND (owner = :owner OR expiresAtEpochMs < :now)")
    fun acquireOrRenewLease(sessionId: String, owner: String, now: Long, expiresAt: Long): Int

    @Query("DELETE FROM metadata_apply_leases WHERE applySessionId = :sessionId AND owner = :owner")
    fun releaseLease(sessionId: String, owner: String): Int

    @Query("SELECT * FROM metadata_apply_leases WHERE applySessionId = :sessionId")
    fun findLease(sessionId: String): MetadataApplyLeaseEntity?
}
