package com.theemrock.smoothmedia.catalog.reconciliation

import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.ScanCheckpointCoverage
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceScanCheckpointEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.discovery.AudioFormat
import com.theemrock.smoothmedia.discovery.AudioDiscoveryResult
import com.theemrock.smoothmedia.discovery.DiscoveredAudioFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanEvidenceFactoryTest {
    @Test
    fun completedErrorFreeTraversal_producesCompleteEvidence() {
        val evidence = ScanEvidenceFactory.from(source(), session(ScanSessionStatus.COMPLETED), checkpoint(ScanCheckpointCoverage.COMPLETE, 2, 2), discovery(2))

        assertTrue(evidence.sourceReady)
        assertTrue(evidence.scopeComplete)
        assertTrue(evidence.scanSucceeded)
        assertEquals(0, evidence.readErrors)
    }

    @Test
    fun unreadableEntries_preventCompleteEvidence() {
        val evidence = ScanEvidenceFactory.from(source(), session(ScanSessionStatus.COMPLETED), checkpoint(ScanCheckpointCoverage.COMPLETE, 2, 2), discovery(2, skipped = 1))

        assertFalse(evidence.scopeComplete)
        assertFalse(evidence.scanSucceeded)
        assertEquals(1, evidence.readErrors)
    }

    @Test
    fun partialCheckpointAndInterruptedSession_preventCompleteEvidence() {
        val evidence = ScanEvidenceFactory.from(source(), session(ScanSessionStatus.PAUSED), checkpoint(ScanCheckpointCoverage.PARTIAL, 2, 1), discovery(2))

        assertFalse(evidence.scopeComplete)
        assertFalse(evidence.scanSucceeded)
    }

    @Test
    fun permissionLoss_isNotPhysicalMissingEvidence() {
        val evidence = ScanEvidenceFactory.from(source(permission = SourcePermissionState.LOST, availability = SourceAvailability.PERMISSION_LOST), session(ScanSessionStatus.FAILED), checkpoint(ScanCheckpointCoverage.PARTIAL, 0, 0), AudioDiscoveryResult.PermissionLost("source-1"))

        assertFalse(evidence.sourceReady)
        assertEquals(ScanIneligibility.PERMISSION_LOST, MissingReconciliationPolicy().evaluate(evidence, emptyList(), emptySet()).reason)
    }

    @Test
    fun failedDiscovery_isNotCompleteEvidence() {
        val evidence = ScanEvidenceFactory.from(source(), session(ScanSessionStatus.FAILED), checkpoint(ScanCheckpointCoverage.PARTIAL, 0, 0), AudioDiscoveryResult.Failed("source-1"))

        assertFalse(evidence.scopeComplete)
        assertFalse(evidence.scanSucceeded)
    }

    @Test
    fun retryAfterPartialCheckpoint_requiresFreshCompleteCheckpoint() {
        val interrupted = ScanEvidenceFactory.from(
            source(),
            session(ScanSessionStatus.PAUSED),
            checkpoint(ScanCheckpointCoverage.PARTIAL, 2, 1),
            discovery(2),
        )
        val retried = ScanEvidenceFactory.from(
            source(),
            session(ScanSessionStatus.COMPLETED),
            checkpoint(ScanCheckpointCoverage.COMPLETE, 2, 2),
            discovery(2),
        )

        assertFalse(interrupted.scanSucceeded)
        assertTrue(retried.scanSucceeded)
    }

    private fun source(
        permission: SourcePermissionState = SourcePermissionState.GRANTED,
        availability: SourceAvailability = SourceAvailability.AVAILABLE,
    ) = LibrarySourceEntity("source-1", LibrarySourceType.SAF_TREE, "content://tree/root", null, 1, permission, availability, 1, 1, SourceSelectionState.ACTIVE)

    private fun session(status: ScanSessionStatus) = ScanSessionEntity("scan-1", status, 1, 1, if (status == ScanSessionStatus.COMPLETED) 2 else null)

    private fun checkpoint(coverage: ScanCheckpointCoverage, discovered: Int, processed: Int) = SourceScanCheckpointEntity("scan-1", "source-1", null, discovered, processed, coverage, 2)

    private fun discovery(count: Int, skipped: Int = 0) = AudioDiscoveryResult.Completed(
        "source-1",
        List(count) { index ->
            DiscoveredAudioFile(
                sourceId = "source-1",
                uri = "content://tree/file-$index",
                documentId = "doc-$index",
                displayName = "file-$index.mp3",
                format = AudioFormat.MP3,
                mimeType = "audio/mpeg",
                sizeBytes = 1,
                modifiedAtEpochMs = 1,
            )
        },
        skipped,
    )
}
