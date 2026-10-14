package com.theemrock.smoothmedia.catalog.reconciliation

import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateState
import com.theemrock.smoothmedia.catalog.database.entity.ScanCheckpointCoverage
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash
import com.theemrock.smoothmedia.catalog.model.FileHash
import com.theemrock.smoothmedia.catalog.model.HashAlgorithmVersion
import com.theemrock.smoothmedia.discovery.AudioDiscoveryResult

/** Applies availability decisions only after re-reading complete scan evidence in one Room transaction. */
class RoomAvailabilityReconciliation(
    private val database: CatalogDatabase,
    private val policy: MissingReconciliationPolicy = MissingReconciliationPolicy(),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    fun reconcile(
        sessionId: String,
        sourceId: String,
        discovery: AudioDiscoveryResult,
    ): RoomReconciliationResult {
        var result: RoomReconciliationResult? = null
        database.runInTransaction {
            result = reconcileInTransaction(sessionId, sourceId, discovery)
        }
        return checkNotNull(result)
    }

    private fun reconcileInTransaction(
        sessionId: String,
        sourceId: String,
        discovery: AudioDiscoveryResult,
    ): RoomReconciliationResult {
        val source = database.librarySourceDao().find(sourceId)
            ?: return RoomReconciliationResult.blocked(RoomReconciliationBlock.SOURCE_NOT_FOUND)
        val session = database.scanDao().findSession(sessionId)
            ?: return RoomReconciliationResult.blocked(RoomReconciliationBlock.SESSION_NOT_FOUND)
        val checkpoint = database.scanDao().findCheckpoint(sessionId, sourceId)
            ?: return RoomReconciliationResult.blocked(RoomReconciliationBlock.CHECKPOINT_NOT_FOUND)
        val candidates = database.scanDao().listCandidates(sessionId, sourceId)
        val evidence = ScanEvidenceFactory.from(source, session, checkpoint, discovery)
        if (evidence.coverage != ScanCheckpointCoverage.COMPLETE ||
            evidence.sessionStatus != ScanSessionStatus.COMPLETED ||
            !evidence.scopeComplete ||
            !evidence.sourceReady ||
            !evidence.scanSucceeded ||
            evidence.readErrors > 0
        ) {
            return RoomReconciliationResult.blocked(
                evidence.ineligibilityReason(),
            )
        }
        val completed = discovery as? AudioDiscoveryResult.Completed
            ?: return RoomReconciliationResult.blocked(RoomReconciliationBlock.DISCOVERY_INCOMPLETE)
        if (candidates.size != completed.files.size ||
            candidates.any { it.state != ScanCandidateState.COMPLETED || it.locationId == null }
        ) {
            return RoomReconciliationResult.blocked(RoomReconciliationBlock.CANDIDATES_INCOMPLETE)
        }

        val locations = database.trackDao().listLocations()
            .filter { it.sourceId == sourceId }
        val bindings = database.locatorBindingDao().listAll()
            .filter { it.sourceId == sourceId }
            .associateBy { it.locationId }
        val reconciliationLocations = locations.mapNotNull { location ->
            val binding = bindings[location.locationId] ?: return@mapNotNull null
            val excluded = database.catalogExclusionDao().isExcluded(
                sourceId = sourceId,
                locator = binding.locator,
                algorithmName = location.currentFileHash.algorithmName,
                algorithmVersion = location.currentFileHash.algorithmVersion,
                digest = location.currentFileHash.digest,
            )
            ReconciliationLocation(
                locationId = location.locationId,
                sourceId = location.sourceId,
                format = location.format,
                availability = location.availability,
                originalHash = location.originalFileHash.toFileHash(),
                currentHash = location.currentFileHash.toFileHash(),
                excluded = excluded,
            )
        }
        val locationsById = locations.associateBy { it.locationId }
        val observed = candidates.mapNotNull { candidate ->
            val locationId = candidate.locationId ?: return@mapNotNull null
            val location = locationsById[locationId] ?: return@mapNotNull null
            ObservedLocation(
                locationId = location.locationId,
                format = location.format,
                hash = location.currentFileHash.toFileHash(),
            )
        }.toSet()
        val decisions = policy.evaluate(evidence, reconciliationLocations, observed).updates
        val applied = decisions.filter { decision ->
            database.trackDao().updateAvailability(
                locationId = decision.locationId,
                availability = decision.availability,
                updatedAtEpochMs = nowEpochMs(),
            ) == 1
        }
        return RoomReconciliationResult(applied, null)
    }

    private fun ScanEvidence.ineligibilityReason(): RoomReconciliationBlock = when {
        !sourceReady -> RoomReconciliationBlock.PERMISSION_LOST
        coverage != ScanCheckpointCoverage.COMPLETE -> RoomReconciliationBlock.PARTIAL_COVERAGE
        !scopeComplete -> RoomReconciliationBlock.SCOPE_INCOMPLETE
        sessionStatus == ScanSessionStatus.PAUSED -> RoomReconciliationBlock.SCAN_PAUSED
        sessionStatus == ScanSessionStatus.CANCELLED -> RoomReconciliationBlock.SCAN_CANCELLED
        sessionStatus == ScanSessionStatus.FAILED || !scanSucceeded -> RoomReconciliationBlock.SCAN_FAILED
        readErrors > 0 -> RoomReconciliationBlock.READ_ERROR
        else -> RoomReconciliationBlock.EVIDENCE_INVALID
    }

    private fun StoredFileHash.toFileHash() = FileHash(
        HashAlgorithmVersion(algorithmName, algorithmVersion),
        digest,
    )
}

data class RoomReconciliationResult(
    val applied: List<LocationDecision>,
    val blocked: RoomReconciliationBlock?,
) {
    companion object {
        fun blocked(reason: RoomReconciliationBlock) = RoomReconciliationResult(emptyList(), reason)
    }
}

enum class RoomReconciliationBlock {
    SOURCE_NOT_FOUND,
    SESSION_NOT_FOUND,
    CHECKPOINT_NOT_FOUND,
    DISCOVERY_INCOMPLETE,
    CANDIDATES_INCOMPLETE,
    PARTIAL_COVERAGE,
    SCOPE_INCOMPLETE,
    SCAN_FAILED,
    SCAN_PAUSED,
    SCAN_CANCELLED,
    PERMISSION_LOST,
    READ_ERROR,
    EVIDENCE_INVALID,
}
