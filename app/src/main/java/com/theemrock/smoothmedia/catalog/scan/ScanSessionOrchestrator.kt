package com.theemrock.smoothmedia.catalog.scan

import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateState
import com.theemrock.smoothmedia.catalog.database.entity.ScanCheckpointCoverage
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourceScanCheckpointEntity
import com.theemrock.smoothmedia.catalog.repository.LeaseRecoveryResult
import com.theemrock.smoothmedia.catalog.repository.ScanLifecycleResult
import com.theemrock.smoothmedia.catalog.repository.ScanRecoveryResult
import com.theemrock.smoothmedia.catalog.repository.ScanStateRepository
import com.theemrock.smoothmedia.discovery.AudioDiscoveryResult
import com.theemrock.smoothmedia.discovery.DiscoveredAudioFile

/** Coordinates one manually started discovery scan without performing hash or catalog ingestion. */
class ScanSessionOrchestrator(
    private val state: ScanStateRepository,
    private val discover: (LibrarySourceEntity) -> AudioDiscoveryResult,
    private val beforeCandidate: (sessionId: String, file: DiscoveredAudioFile) -> Unit = { _, _ -> },
    private val processor: ScanCandidateProcessor? = null,
    private val afterCandidate: (sessionId: String, file: DiscoveredAudioFile, result: CandidateProcessingResult.Completed) -> Unit = { _, _, _ -> },
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val onPhaseChanged: (ScanPhase) -> Unit = {},
    private val onTiming: (ScanTiming) -> Unit = {},
) {
    private var timing = ScanTiming()

    fun start(session: ScanSessionEntity, source: LibrarySourceEntity): Result {
        onPhaseChanged(ScanPhase.STARTING)
        val sourceRejection = validateSource(source)
        if (sourceRejection != null) return Result.Rejected(sourceRejection)

        val checkpoint = initialCheckpoint(session.sessionId, source.sourceId, session.updatedAtEpochMs)
        return when (val start = state.recoverStaleLeaseAndStart(session, listOf(checkpoint))) {
            is LeaseRecoveryResult.ResumeRequired -> Result.AlreadyActive(start.sessionId)
            is LeaseRecoveryResult.Started -> execute(session.sessionId, source)
        }
    }

    fun resume(sessionId: String, source: LibrarySourceEntity, updatedAtEpochMs: Long): Result {
        onPhaseChanged(ScanPhase.RESUMING)
        val sourceRejection = validateSource(source)
        if (sourceRejection != null) return Result.Rejected(sourceRejection)
        val checkpoint = state.findCheckpoint(sessionId, source.sourceId)
            ?: return Result.Rejected(RejectionReason.CHECKPOINT_MISSING)
        when (state.recoverInterruptedSession(sessionId, updatedAtEpochMs)) {
            is ScanRecoveryResult.Rejected -> return Result.Rejected(RejectionReason.RECOVERY_REJECTED)
            is ScanRecoveryResult.Recovered -> Unit
        }
        when (val resumed = state.resumeSession(sessionId, updatedAtEpochMs)) {
            is ScanLifecycleResult.Rejected -> return Result.Rejected(RejectionReason.LIFECYCLE_REJECTED)
            is ScanLifecycleResult.Applied -> Unit
        }
        return execute(sessionId, source, checkpoint)
    }

    fun pause(sessionId: String, updatedAtEpochMs: Long): Result {
        onPhaseChanged(ScanPhase.PAUSING)
        return lifecycleResult(state.pauseSession(sessionId, updatedAtEpochMs))
    }

    fun cancel(sessionId: String, updatedAtEpochMs: Long): Result {
        onPhaseChanged(ScanPhase.STOPPING)
        return lifecycleResult(state.cancelSession(sessionId, updatedAtEpochMs))
    }

    private fun lifecycleResult(result: ScanLifecycleResult): Result = when (result) {
        is ScanLifecycleResult.Applied -> when (result.status) {
            ScanSessionStatus.PAUSED -> {
                onPhaseChanged(ScanPhase.PAUSED)
                Result.Paused(result.sessionId, 0)
            }
            ScanSessionStatus.CANCELLED -> {
                onPhaseChanged(ScanPhase.CANCELLED)
                Result.Cancelled(result.sessionId, 0)
            }
            else -> Result.Rejected(RejectionReason.LIFECYCLE_REJECTED)
        }
        is ScanLifecycleResult.Rejected -> Result.Rejected(RejectionReason.LIFECYCLE_REJECTED)
    }

    private fun execute(
        sessionId: String,
        source: LibrarySourceEntity,
        checkpoint: SourceScanCheckpointEntity? = null,
    ): Result {
        val discoveryStartedAt = System.nanoTime()
        state.heartbeat(sessionId, nowEpochMs())
        onPhaseChanged(ScanPhase.DISCOVERING)
        val discovery = discover(source)
        val discoveryMs = (System.nanoTime() - discoveryStartedAt) / 1_000_000L
        timing = timing.copy(discoveryMs = discoveryMs)
        onTiming(timing)
        if (discovery !is AudioDiscoveryResult.Completed) {
            onPhaseChanged(ScanPhase.FAILED)
            state.failSession(sessionId, nowEpochMs())
            return Result.DiscoveryFailed(discovery)
        }
        onPhaseChanged(ScanPhase.PROCESSING)

        var processedCount = checkpoint?.processedCount ?: 0
        var newlyRecorded = 0
        var alreadyRecorded = 0
        discovery.files.forEach { file ->
            state.heartbeat(sessionId, nowEpochMs())
            beforeCandidate(sessionId, file)
            when (state.findSession(sessionId)?.status) {
                ScanSessionStatus.PAUSED -> {
                    onPhaseChanged(ScanPhase.PAUSED)
                    return Result.Paused(sessionId, processedCount)
                }
                ScanSessionStatus.CANCELLED -> {
                    onPhaseChanged(ScanPhase.CANCELLED)
                    return Result.Cancelled(sessionId, processedCount)
                }
                ScanSessionStatus.RUNNING -> Unit
                else -> return Result.Rejected(RejectionReason.SESSION_NOT_RUNNING)
            }

            val candidateStartedAt = System.nanoTime()
            processedCount++
            val existing = state.findCandidate(sessionId, source.sourceId, file.documentId)
            if (existing == null) {
                state.recordCandidate(
                    ScanCandidateEntity(
                        sessionId = sessionId,
                        sourceId = source.sourceId,
                        candidateKey = file.documentId,
                        state = ScanCandidateState.DISCOVERED,
                        locationId = null,
                        updatedAtEpochMs = nowEpochMs(),
                    ),
                )
                newlyRecorded++
            } else {
                alreadyRecorded++
            }
            var completedResult: CandidateProcessingResult.Completed? = null
            if (processor != null && (existing?.state != ScanCandidateState.COMPLETED || processor.reprocessCompletedCandidates())) {
                val reprocessingCompleted = existing?.state == ScanCandidateState.COMPLETED
                if (!reprocessingCompleted) {
                    state.updateCandidateState(sessionId, source.sourceId, file.documentId, ScanCandidateState.PROCESSING, null, nowEpochMs())
                }
                when (val processed = if (reprocessingCompleted) processor.processCompletedCandidate(sessionId, file, existing!!) else processor.process(sessionId, file)) {
                    is CandidateProcessingResult.Completed -> {
                        if (!reprocessingCompleted) state.updateCandidateState(sessionId, source.sourceId, file.documentId, ScanCandidateState.COMPLETED, processed.locationId, nowEpochMs())
                        completedResult = processed
                    }
                    is CandidateProcessingResult.Failed -> {
                        if (!reprocessingCompleted) state.updateCandidateState(sessionId, source.sourceId, file.documentId, ScanCandidateState.DISCOVERED, null, nowEpochMs())
                        onPhaseChanged(ScanPhase.FAILED)
                        state.failSession(sessionId, nowEpochMs())
                        return Result.CandidateFailed(sessionId, file.documentId, processed.reason)
                    }
                    CandidateProcessingResult.Paused -> {
                        if (!reprocessingCompleted) state.updateCandidateState(sessionId, source.sourceId, file.documentId, ScanCandidateState.DISCOVERED, null, nowEpochMs())
                        onPhaseChanged(ScanPhase.PAUSED)
                        return Result.Paused(sessionId, processedCount)
                    }
                    CandidateProcessingResult.Cancelled -> {
                        if (!reprocessingCompleted) state.updateCandidateState(sessionId, source.sourceId, file.documentId, ScanCandidateState.DISCOVERED, null, nowEpochMs())
                        onPhaseChanged(ScanPhase.CANCELLED)
                        return Result.Cancelled(sessionId, processedCount)
                    }
                }
            }
            val checkpointStartedAt = System.nanoTime()
            state.updateCheckpoint(sessionId, source.sourceId, file.documentId, discovery.files.size, processedCount, ScanCheckpointCoverage.PARTIAL, nowEpochMs())
            val checkpointMs = (System.nanoTime() - checkpointStartedAt) / 1_000_000L
            timing = timing.copy(
                processingMs = timing.processingMs + (System.nanoTime() - candidateStartedAt) / 1_000_000L,
                lastCheckpointMs = checkpointMs,
            )
            onTiming(timing)
            completedResult?.let { afterCandidate(sessionId, file, it) }
        }
        when (state.findSession(sessionId)?.status) {
            ScanSessionStatus.PAUSED -> {
                onPhaseChanged(ScanPhase.PAUSED)
                return Result.Paused(sessionId, processedCount)
            }
            ScanSessionStatus.CANCELLED -> {
                onPhaseChanged(ScanPhase.CANCELLED)
                return Result.Cancelled(sessionId, processedCount)
            }
            ScanSessionStatus.RUNNING -> Unit
            else -> return Result.Rejected(RejectionReason.SESSION_NOT_RUNNING)
        }
        state.updateCheckpoint(sessionId, source.sourceId, null, discovery.files.size, processedCount, if (discovery.skippedUnreadableCount == 0) ScanCheckpointCoverage.COMPLETE else ScanCheckpointCoverage.PARTIAL, nowEpochMs())
        state.completeSession(sessionId, nowEpochMs())
        return when (state.findSession(sessionId)?.status) {
            ScanSessionStatus.COMPLETED -> {
                onPhaseChanged(ScanPhase.COMPLETED)
                Result.Completed(sessionId, newlyRecorded, alreadyRecorded, discovery.skippedUnreadableCount)
            }
            ScanSessionStatus.PAUSED -> {
                onPhaseChanged(ScanPhase.PAUSED)
                Result.Paused(sessionId, processedCount)
            }
            ScanSessionStatus.CANCELLED -> {
                onPhaseChanged(ScanPhase.CANCELLED)
                Result.Cancelled(sessionId, processedCount)
            }
            else -> Result.Rejected(RejectionReason.SESSION_NOT_RUNNING)
        }
    }

    private fun validateSource(source: LibrarySourceEntity): RejectionReason? = when {
        source.sourceType != LibrarySourceType.SAF_TREE -> RejectionReason.SOURCE_NOT_SELECTED
        source.selectionState != com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState.ACTIVE ->
            RejectionReason.SOURCE_NOT_SELECTED
        source.permissionState != SourcePermissionState.GRANTED -> RejectionReason.SOURCE_NOT_READY
        source.availability != SourceAvailability.AVAILABLE -> RejectionReason.SOURCE_NOT_READY
        else -> null
    }

    private fun initialCheckpoint(sessionId: String, sourceId: String, nowEpochMs: Long) =
        SourceScanCheckpointEntity(
            sessionId = sessionId,
            sourceId = sourceId,
            continuationToken = null,
            discoveredCount = 0,
            processedCount = 0,
            coverage = ScanCheckpointCoverage.PARTIAL,
            updatedAtEpochMs = nowEpochMs,
        )

    sealed interface Result {
        data class Completed(
            val sessionId: String,
            val newlyRecordedCandidates: Int,
            val alreadyRecordedCandidates: Int,
            val skippedUnreadableCount: Int,
        ) : Result

        data class Paused(val sessionId: String, val processedCount: Int) : Result

        data class Cancelled(val sessionId: String, val processedCount: Int) : Result

        data class AlreadyActive(val sessionId: String) : Result

        data class DiscoveryFailed(val result: AudioDiscoveryResult) : Result

        data class CandidateFailed(
            val sessionId: String,
            val candidateKey: String,
            val reason: FailureReason,
        ) : Result

        data class Rejected(val reason: RejectionReason) : Result
    }

    enum class RejectionReason {
        SOURCE_NOT_SELECTED,
        SOURCE_NOT_READY,
        CHECKPOINT_MISSING,
        SESSION_NOT_RUNNING,
        LIFECYCLE_REJECTED,
        RECOVERY_REJECTED,
    }
}

enum class ScanPhase {
    IDLE,
    STARTING,
    DISCOVERING,
    PROCESSING,
    PAUSING,
    PAUSED,
    RESUMING,
    STOPPING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

data class ScanTiming(
    val discoveryMs: Long = 0,
    val processingMs: Long = 0,
    val lastCheckpointMs: Long = 0,
)
