package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theemrock.smoothmedia.catalog.database.entity.ActiveScanLeaseEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateState
import com.theemrock.smoothmedia.catalog.database.entity.ScanCheckpointCoverage
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.catalog.database.entity.SourceScanCheckpointEntity

@Dao
interface ScanDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSession(session: ScanSessionEntity)

    @Query("SELECT * FROM scan_sessions WHERE session_id = :sessionId")
    fun findSession(sessionId: String): ScanSessionEntity?

    @Query("SELECT * FROM scan_sessions WHERE status IN ('RUNNING', 'PAUSED') ORDER BY updated_at_epoch_ms DESC LIMIT 1")
    fun findLatestResumableSession(): ScanSessionEntity?

    @Query("INSERT INTO active_scan_leases(slot_id, session_id) VALUES(1, :sessionId)")
    fun acquireGlobalLease(sessionId: String)

    @Query("SELECT * FROM active_scan_leases WHERE slot_id = 1")
    fun findActiveLease(): ActiveScanLeaseEntity?

    @Query("DELETE FROM active_scan_leases WHERE slot_id = 1")
    fun clearGlobalLease(): Int

    @Query("DELETE FROM active_scan_leases WHERE slot_id = 1 AND session_id = :sessionId")
    fun releaseLease(sessionId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertCheckpoint(checkpoint: SourceScanCheckpointEntity)

    @Query(
        """
        UPDATE source_scan_checkpoints
        SET continuation_token = :continuationToken,
            discovered_count = :discoveredCount,
            processed_count = :processedCount,
            coverage = :coverage,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE session_id = :sessionId AND source_id = :sourceId
        """,
    )
    fun updateCheckpoint(
        sessionId: String,
        sourceId: String,
        continuationToken: String?,
        discoveredCount: Int,
        processedCount: Int,
        coverage: ScanCheckpointCoverage,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        SELECT * FROM source_scan_checkpoints
        WHERE session_id = :sessionId AND source_id = :sourceId
        """,
    )
    fun findCheckpoint(sessionId: String, sourceId: String): SourceScanCheckpointEntity?

    @Query("SELECT * FROM source_scan_checkpoints WHERE session_id = :sessionId ORDER BY updated_at_epoch_ms DESC LIMIT 1")
    fun findLatestCheckpoint(sessionId: String): SourceScanCheckpointEntity?

    @Query("SELECT MAX(updated_at_epoch_ms) FROM source_scan_checkpoints WHERE session_id = :sessionId")
    fun latestCheckpointUpdatedAt(sessionId: String): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertCandidate(candidate: ScanCandidateEntity)

    @Query(
        """
        SELECT * FROM scan_candidates
        WHERE session_id = :sessionId
          AND source_id = :sourceId
          AND candidate_key = :candidateKey
        """,
    )
    fun findCandidate(
        sessionId: String,
        sourceId: String,
        candidateKey: String,
    ): ScanCandidateEntity?

    @Query(
        """
        SELECT * FROM scan_candidates
        WHERE session_id = :sessionId AND source_id = :sourceId
        ORDER BY candidate_key
        """,
    )
    fun listCandidates(sessionId: String, sourceId: String): List<ScanCandidateEntity>

    @Query(
        """
        UPDATE scan_candidates
        SET state = :state,
            location_id = :locationId,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE session_id = :sessionId
          AND source_id = :sourceId
          AND candidate_key = :candidateKey
        """,
    )
    fun updateCandidateState(
        sessionId: String,
        sourceId: String,
        candidateKey: String,
        state: ScanCandidateState,
        locationId: String?,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE scan_candidates
        SET state = :retryableState,
            location_id = NULL,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE session_id = :sessionId AND state = :processingState
        """,
    )
    fun resetProcessingCandidates(
        sessionId: String,
        processingState: ScanCandidateState,
        retryableState: ScanCandidateState,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE scan_sessions
        SET status = :status,
            updated_at_epoch_ms = :updatedAtEpochMs,
            completed_at_epoch_ms = :completedAtEpochMs
        WHERE session_id = :sessionId
        """,
    )
    fun updateSessionStatus(
        sessionId: String,
        status: ScanSessionStatus,
        updatedAtEpochMs: Long,
        completedAtEpochMs: Long?,
    ): Int

    @Query(
        """
        UPDATE scan_sessions
        SET status = 'COMPLETED',
            updated_at_epoch_ms = :completedAtEpochMs,
            completed_at_epoch_ms = :completedAtEpochMs
        WHERE session_id = :sessionId
          AND status = 'RUNNING'
          AND EXISTS(
              SELECT 1 FROM active_scan_leases
              WHERE slot_id = 1 AND session_id = :sessionId
          )
        """,
    )
    fun completeRunningSessionWithLease(sessionId: String, completedAtEpochMs: Long): Int

    @Query("UPDATE scan_sessions SET updated_at_epoch_ms = :updatedAtEpochMs WHERE session_id = :sessionId")
    fun touchSession(sessionId: String, updatedAtEpochMs: Long): Int

    @Query(
        """
        UPDATE scan_sessions
        SET updated_at_epoch_ms = :updatedAtEpochMs
        WHERE session_id = :sessionId
          AND status = 'RUNNING'
          AND EXISTS(
              SELECT 1 FROM active_scan_leases
              WHERE slot_id = 1 AND session_id = :sessionId
          )
        """,
    )
    fun heartbeat(sessionId: String, updatedAtEpochMs: Long): Int

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM scan_sessions AS session
            JOIN source_scan_checkpoints AS checkpoint
              ON checkpoint.session_id = session.session_id
            WHERE session.session_id = :sessionId
              AND checkpoint.source_id = :sourceId
              AND session.status = :completedStatus
              AND checkpoint.coverage = :completeCoverage
        )
        """,
    )
    fun isMissingReconciliationAllowed(
        sessionId: String,
        sourceId: String,
        completedStatus: ScanSessionStatus,
        completeCoverage: ScanCheckpointCoverage,
    ): Boolean
}
