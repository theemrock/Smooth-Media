package com.theemrock.smoothmedia.catalog.scan

import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateState
import com.theemrock.smoothmedia.catalog.database.entity.ScanCheckpointCoverage
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceScanCheckpointEntity
import com.theemrock.smoothmedia.catalog.repository.LeaseRecoveryResult
import com.theemrock.smoothmedia.catalog.repository.ScanLifecycleResult
import com.theemrock.smoothmedia.catalog.repository.ScanRecoveryResult
import com.theemrock.smoothmedia.catalog.repository.ScanStateRepository
import com.theemrock.smoothmedia.discovery.AudioDiscoveryResult
import com.theemrock.smoothmedia.discovery.AudioFormat
import com.theemrock.smoothmedia.discovery.DiscoveredAudioFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScanSessionPauseRaceTest {
    @Test
    fun pauseAfterLastCandidate_doesNotGetOverwrittenByCompletion() {
        val state = PauseAwareScanState()
        val orchestrator = ScanSessionOrchestrator(
            state = state,
            discover = { AudioDiscoveryResult.Completed("source", listOf(file("last")), 0) },
            processor = ScanCandidateProcessor { _, _ ->
                CandidateProcessingResult.Completed(locationId = "location-last")
            },
            afterCandidate = { sessionId, _, _ ->
                state.pauseSession(sessionId, updatedAtEpochMs = 2L)
            },
        )

        val result = orchestrator.start(session(), source())

        assertEquals(
            ScanSessionOrchestrator.Result.Paused("scan", processedCount = 1),
            result,
        )
        assertEquals(ScanSessionStatus.PAUSED, state.status)
        assertFalse(state.leaseHeld)
        assertEquals(ScanCheckpointCoverage.PARTIAL, state.checkpoint.coverage)
    }

    private fun session() = ScanSessionEntity(
        sessionId = "scan",
        status = ScanSessionStatus.RUNNING,
        startedAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        completedAtEpochMs = null,
    )

    private fun source() = LibrarySourceEntity(
        sourceId = "source",
        sourceType = LibrarySourceType.SAF_TREE,
        rootUri = "content://tree/music",
        volumeName = "music",
        persistedPermissionFlags = 3,
        permissionState = SourcePermissionState.GRANTED,
        availability = SourceAvailability.AVAILABLE,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    private fun file(id: String) = DiscoveredAudioFile(
        sourceId = "source",
        uri = "content://tree/music/$id.mp3",
        documentId = id,
        displayName = "$id.mp3",
        format = AudioFormat.MP3,
        mimeType = "audio/mpeg",
        sizeBytes = 10L,
        modifiedAtEpochMs = 1L,
    )

    private class PauseAwareScanState : ScanStateRepository {
        var status = ScanSessionStatus.RUNNING
        var leaseHeld = false
        var checkpoint = SourceScanCheckpointEntity(
            sessionId = "scan",
            sourceId = "source",
            continuationToken = null,
            discoveredCount = 0,
            processedCount = 0,
            coverage = ScanCheckpointCoverage.PARTIAL,
            updatedAtEpochMs = 1L,
        )

        override fun startSession(session: ScanSessionEntity, checkpoints: List<SourceScanCheckpointEntity>) = Unit

        override fun recoverStaleLeaseAndStart(
            session: ScanSessionEntity,
            checkpoints: List<SourceScanCheckpointEntity>,
        ): LeaseRecoveryResult {
            status = ScanSessionStatus.RUNNING
            leaseHeld = true
            checkpoint = checkpoints.single()
            return LeaseRecoveryResult.Started(session.sessionId, recoveredStaleLease = false)
        }

        override fun findSession(sessionId: String) = ScanSessionEntity(
            sessionId = sessionId,
            status = status,
            startedAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            completedAtEpochMs = null,
        )

        override fun updateCheckpoint(
            sessionId: String,
            sourceId: String,
            continuationToken: String?,
            discoveredCount: Int,
            processedCount: Int,
            coverage: ScanCheckpointCoverage,
            updatedAtEpochMs: Long,
        ): Boolean {
            checkpoint = checkpoint.copy(
                continuationToken = continuationToken,
                discoveredCount = discoveredCount,
                processedCount = processedCount,
                coverage = coverage,
                updatedAtEpochMs = updatedAtEpochMs,
            )
            return true
        }

        override fun findCheckpoint(sessionId: String, sourceId: String) = checkpoint

        override fun heartbeat(sessionId: String, updatedAtEpochMs: Long) = status == ScanSessionStatus.RUNNING && leaseHeld

        override fun recordCandidate(candidate: ScanCandidateEntity) = Unit

        override fun findCandidate(sessionId: String, sourceId: String, candidateKey: String): ScanCandidateEntity? = null

        override fun updateCandidateState(
            sessionId: String,
            sourceId: String,
            candidateKey: String,
            state: ScanCandidateState,
            locationId: String?,
            updatedAtEpochMs: Long,
        ) = true

        override fun recoverInterruptedSession(sessionId: String, updatedAtEpochMs: Long, staleAfterMs: Long) =
            ScanRecoveryResult.Recovered(sessionId, 0)

        override fun completeSession(sessionId: String, completedAtEpochMs: Long) {
            if (status == ScanSessionStatus.RUNNING && leaseHeld) {
                status = ScanSessionStatus.COMPLETED
                leaseHeld = false
            }
        }

        override fun pauseSession(sessionId: String, updatedAtEpochMs: Long): ScanLifecycleResult {
            if (status != ScanSessionStatus.RUNNING || !leaseHeld) {
                return ScanLifecycleResult.Rejected(ScanLifecycleResult.RejectionReason.INVALID_STATE)
            }
            status = ScanSessionStatus.PAUSED
            leaseHeld = false
            return ScanLifecycleResult.Applied(sessionId, ScanSessionStatus.PAUSED)
        }

        override fun resumeSession(sessionId: String, updatedAtEpochMs: Long) =
            ScanLifecycleResult.Rejected(ScanLifecycleResult.RejectionReason.INVALID_STATE)

        override fun cancelSession(sessionId: String, updatedAtEpochMs: Long) =
            ScanLifecycleResult.Rejected(ScanLifecycleResult.RejectionReason.INVALID_STATE)

        override fun failSession(sessionId: String, updatedAtEpochMs: Long) =
            ScanLifecycleResult.Rejected(ScanLifecycleResult.RejectionReason.INVALID_STATE)

        override fun isMissingReconciliationAllowed(sessionId: String, sourceId: String) = false
    }
}
