package com.theemrock.smoothmedia.catalog.repository

import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanCheckpointCoverage
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceScanCheckpointEntity
import com.theemrock.smoothmedia.catalog.scan.ScanLeaseRecoveryPolicy

interface ScanStateRepository {
    fun startSession(
        session: ScanSessionEntity,
        checkpoints: List<SourceScanCheckpointEntity>,
    )

    fun recoverStaleLeaseAndStart(
        session: ScanSessionEntity,
        checkpoints: List<SourceScanCheckpointEntity>,
    ): LeaseRecoveryResult

    fun findSession(sessionId: String): ScanSessionEntity?

    fun updateCheckpoint(
        sessionId: String,
        sourceId: String,
        continuationToken: String?,
        discoveredCount: Int,
        processedCount: Int,
        coverage: ScanCheckpointCoverage,
        updatedAtEpochMs: Long,
    ): Boolean

    fun findCheckpoint(sessionId: String, sourceId: String): SourceScanCheckpointEntity?

    /** Refreshes a live session lease without changing checkpoint or catalog data. */
    fun heartbeat(sessionId: String, updatedAtEpochMs: Long): Boolean = true

    fun recordCandidate(candidate: ScanCandidateEntity)

    fun findCandidate(
        sessionId: String,
        sourceId: String,
        candidateKey: String,
    ): ScanCandidateEntity?

    fun updateCandidateState(
        sessionId: String,
        sourceId: String,
        candidateKey: String,
        state: com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateState,
        locationId: String?,
        updatedAtEpochMs: Long,
    ): Boolean

    fun recoverInterruptedSession(
        sessionId: String,
        updatedAtEpochMs: Long,
        staleAfterMs: Long = ScanLeaseRecoveryPolicy.DEFAULT_STALE_AFTER_MS,
    ): ScanRecoveryResult

    fun completeSession(sessionId: String, completedAtEpochMs: Long)

    fun pauseSession(sessionId: String, updatedAtEpochMs: Long): ScanLifecycleResult

    fun resumeSession(sessionId: String, updatedAtEpochMs: Long): ScanLifecycleResult

    fun cancelSession(sessionId: String, updatedAtEpochMs: Long): ScanLifecycleResult

    fun failSession(sessionId: String, updatedAtEpochMs: Long): ScanLifecycleResult

    fun isMissingReconciliationAllowed(sessionId: String, sourceId: String): Boolean
}

sealed interface ScanLifecycleResult {
    data class Applied(val sessionId: String, val status: com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus) : ScanLifecycleResult

    data class Rejected(val reason: RejectionReason) : ScanLifecycleResult

    enum class RejectionReason {
        NOT_FOUND,
        INVALID_STATE,
        ACTIVE_SCAN_EXISTS,
        LEASE_NOT_OWNED,
    }
}

sealed interface LeaseRecoveryResult {
    data class Started(
        val sessionId: String,
        val recoveredStaleLease: Boolean,
    ) : LeaseRecoveryResult

    data class ResumeRequired(val sessionId: String) : LeaseRecoveryResult
}

sealed interface ScanRecoveryResult {
    data class Recovered(val sessionId: String, val resetCandidates: Int) : ScanRecoveryResult

    data class Rejected(val reason: RejectionReason) : ScanRecoveryResult

    enum class RejectionReason {
        NOT_FOUND,
        LIVE_SESSION,
        CANCELLED_SESSION,
        TERMINAL_SESSION,
    }
}
