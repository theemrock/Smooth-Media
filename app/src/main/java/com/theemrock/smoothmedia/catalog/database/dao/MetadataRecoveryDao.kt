package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theemrock.smoothmedia.catalog.database.entity.MetadataRecoveryDecisionEntity
import com.theemrock.smoothmedia.catalog.database.entity.MetadataRecoverySessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.MetadataRecoverySessionStatus

@Dao
interface MetadataRecoveryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSession(session: MetadataRecoverySessionEntity)

    @Query("SELECT * FROM metadata_recovery_sessions WHERE session_id = :sessionId")
    fun findSession(sessionId: String): MetadataRecoverySessionEntity?

    @Query("SELECT * FROM metadata_recovery_sessions WHERE status IN ('RUNNING', 'PAUSED') ORDER BY updated_at_epoch_ms DESC LIMIT 1")
    fun findResumableSession(): MetadataRecoverySessionEntity?

    @Query("SELECT * FROM metadata_recovery_sessions ORDER BY updated_at_epoch_ms DESC LIMIT 1")
    fun findLatestSession(): MetadataRecoverySessionEntity?

    @Query("SELECT * FROM metadata_recovery_sessions WHERE status = 'COMPLETED' ORDER BY completed_at_epoch_ms DESC LIMIT 1")
    fun findLatestCompletedSession(): MetadataRecoverySessionEntity?

    @Query(
        """
        UPDATE metadata_recovery_sessions
        SET lease_owner = :owner,
            lease_expires_at_epoch_ms = :expiresAtEpochMs,
            updated_at_epoch_ms = :nowEpochMs
        WHERE session_id = :sessionId
          AND status = 'RUNNING'
          AND (lease_owner IS NULL OR lease_expires_at_epoch_ms < :nowEpochMs OR lease_owner = :owner)
        """,
    )
    fun acquireOrRenewLease(sessionId: String, owner: String, nowEpochMs: Long, expiresAtEpochMs: Long): Int

    @Query(
        """
        UPDATE metadata_recovery_sessions
        SET status = 'PAUSED',
            updated_at_epoch_ms = :nowEpochMs,
            lease_owner = NULL,
            lease_expires_at_epoch_ms = NULL
        WHERE session_id = :sessionId
          AND status = 'RUNNING'
          AND (lease_owner IS NULL OR lease_owner = :owner)
        """,
    )
    fun pauseSession(sessionId: String, owner: String, nowEpochMs: Long): Int

    @Query(
        """
        UPDATE metadata_recovery_sessions
        SET status = 'RUNNING',
            updated_at_epoch_ms = :nowEpochMs
        WHERE session_id = :sessionId
          AND status = 'PAUSED'
          AND (lease_owner IS NULL OR lease_expires_at_epoch_ms < :nowEpochMs)
        """,
    )
    fun resumeSession(sessionId: String, nowEpochMs: Long): Int

    @Query(
        """
        UPDATE metadata_recovery_sessions
        SET status = 'CANCELLED',
            updated_at_epoch_ms = :nowEpochMs,
            completed_at_epoch_ms = :nowEpochMs,
            lease_owner = NULL,
            lease_expires_at_epoch_ms = NULL
        WHERE session_id = :sessionId
          AND status IN ('RUNNING', 'PAUSED')
          AND (lease_owner IS NULL OR lease_owner = :owner)
        """,
    )
    fun cancelSession(sessionId: String, owner: String, nowEpochMs: Long): Int

    @Query(
        """
        UPDATE metadata_recovery_sessions
        SET status = 'FAILED',
            updated_at_epoch_ms = :nowEpochMs,
            completed_at_epoch_ms = :nowEpochMs,
            lease_owner = NULL,
            lease_expires_at_epoch_ms = NULL
        WHERE session_id = :sessionId
          AND status = 'RUNNING'
          AND (lease_owner IS NULL OR lease_owner = :owner)
        """,
    )
    fun failSession(sessionId: String, owner: String, nowEpochMs: Long): Int

    @Query("UPDATE metadata_recovery_sessions SET lease_owner = NULL, lease_expires_at_epoch_ms = NULL WHERE session_id = :sessionId AND lease_owner = :owner")
    fun releaseLease(sessionId: String, owner: String): Int

    @Query(
        """
        UPDATE metadata_recovery_sessions
        SET status = :status,
            cursor_track_id = :cursorTrackId,
            processed_tracks = processed_tracks + :processedTracks,
            auto_repair_count = auto_repair_count + :autoRepairCount,
            no_change_count = no_change_count + :noChangeCount,
            review_required_count = review_required_count + :reviewRequiredCount,
            unrecoverable_count = unrecoverable_count + :unrecoverableCount,
            read_error_count = read_error_count + :readErrorCount,
            updated_at_epoch_ms = :nowEpochMs,
            completed_at_epoch_ms = :completedAtEpochMs,
            lease_owner = NULL,
            lease_expires_at_epoch_ms = NULL
        WHERE session_id = :sessionId AND lease_owner = :owner
        """,
    )
    fun commitBatch(
        sessionId: String,
        owner: String,
        status: MetadataRecoverySessionStatus,
        cursorTrackId: String?,
        processedTracks: Int,
        autoRepairCount: Int,
        noChangeCount: Int,
        reviewRequiredCount: Int,
        unrecoverableCount: Int,
        readErrorCount: Int,
        nowEpochMs: Long,
        completedAtEpochMs: Long?,
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDecisions(decisions: List<MetadataRecoveryDecisionEntity>)

    @Query("SELECT COUNT(*) FROM metadata_recovery_decisions WHERE session_id = :sessionId")
    fun countDecisions(sessionId: String): Int

    @Query(
        "SELECT * FROM metadata_recovery_decisions " +
            "WHERE session_id = :sessionId ORDER BY track_id, field_name",
    )
    fun listDecisions(sessionId: String): List<MetadataRecoveryDecisionEntity>
}
