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
import com.theemrock.smoothmedia.discovery.AudioDiscoveryResult

/** Builds reconciliation evidence from persisted scan state and the actual discovery result. */
object ScanEvidenceFactory {
    fun from(
        source: LibrarySourceEntity,
        session: ScanSessionEntity,
        checkpoint: SourceScanCheckpointEntity,
        discovery: AudioDiscoveryResult,
    ): ScanEvidence {
        val completed = discovery as? AudioDiscoveryResult.Completed
        val skippedUnreadable = completed?.skippedUnreadableCount ?: 0
        val sourceReady = source.sourceType == LibrarySourceType.SAF_TREE &&
            source.selectionState == SourceSelectionState.ACTIVE &&
            source.permissionState == SourcePermissionState.GRANTED &&
            source.availability == SourceAvailability.AVAILABLE &&
            discovery !is AudioDiscoveryResult.PermissionLost &&
            discovery !is AudioDiscoveryResult.SourceUnavailable &&
            discovery.sourceId() == source.sourceId
        val checkpointComplete = checkpoint.coverage == ScanCheckpointCoverage.COMPLETE &&
            checkpoint.continuationToken == null &&
            checkpoint.sourceId == source.sourceId &&
            completed != null &&
            checkpoint.discoveredCount == completed.files.size &&
            checkpoint.processedCount >= completed.files.size
        val scopeComplete = completed != null &&
            skippedUnreadable == 0 &&
            checkpointComplete
        return ScanEvidence(
            sourceId = source.sourceId,
            coverage = checkpoint.coverage,
            scopeComplete = scopeComplete,
            sourceReady = sourceReady,
            sessionStatus = session.status,
            scanSucceeded = scopeComplete && session.status == ScanSessionStatus.COMPLETED,
            readErrors = skippedUnreadable,
        )
    }

    private fun AudioDiscoveryResult.sourceId(): String = when (this) {
        is AudioDiscoveryResult.Completed -> sourceId
        is AudioDiscoveryResult.PermissionLost -> sourceId
        is AudioDiscoveryResult.SourceUnavailable -> sourceId
        is AudioDiscoveryResult.UnsupportedSource -> sourceId
        is AudioDiscoveryResult.NotSelected -> sourceId
        is AudioDiscoveryResult.InvalidSource -> sourceId
        is AudioDiscoveryResult.Failed -> sourceId
    }
}
