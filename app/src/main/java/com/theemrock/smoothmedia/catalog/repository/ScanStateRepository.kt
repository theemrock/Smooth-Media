package com.theemrock.smoothmedia.catalog.repository

import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanCheckpointCoverage
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceScanCheckpointEntity

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

    fun recordCandidate(candidate: ScanCandidateEntity)

    fun findCandidate(
        sessionId: String,
        sourceId: String,
        candidateKey: String,
    ): ScanCandidateEntity?

    fun completeSession(sessionId: String, completedAtEpochMs: Long)

    fun isMissingReconciliationAllowed(sessionId: String, sourceId: String): Boolean
}

sealed interface LeaseRecoveryResult {
    data class Started(
        val sessionId: String,
        val recoveredStaleLease: Boolean,
    ) : LeaseRecoveryResult

    data class ResumeRequired(val sessionId: String) : LeaseRecoveryResult
}
