package com.theemrock.smoothmedia.catalog.reconciliation

import com.theemrock.smoothmedia.catalog.database.entity.ScanCheckpointCoverage
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.model.FileHash
import com.theemrock.smoothmedia.catalog.model.LocationAvailability

/**
 * Pure, non-writing policy for deriving availability decisions from a complete scan.
 * Scope completeness is explicit because the current checkpoint schema does not prove it.
 */
class MissingReconciliationPolicy {
    fun evaluate(
        evidence: ScanEvidence,
        locations: List<ReconciliationLocation>,
        observed: Set<ObservedLocation>,
    ): ReconciliationResult {
        val ineligibility = evidence.ineligibility()
        if (ineligibility != null) return ReconciliationResult(emptyList(), ineligibility)

        val observedByLocation = observed.associateBy { it.locationId }
        val updates = locations.asSequence()
            .filter { it.sourceId == evidence.sourceId && !it.excluded }
            .mapNotNull { location ->
                val seen = observedByLocation[location.locationId]
                when {
                    seen != null && seen.matches(location) &&
                        location.availability != LocationAvailability.AVAILABLE ->
                        LocationDecision(location.locationId, LocationAvailability.AVAILABLE)
                    seen != null -> null
                    location.availability == LocationAvailability.AVAILABLE ->
                        LocationDecision(location.locationId, LocationAvailability.MISSING)
                    else -> null
                }
            }
            .toList()
        return ReconciliationResult(updates, null)
    }

    private fun ScanEvidence.ineligibility(): ScanIneligibility? = when {
        !sourceReady -> ScanIneligibility.PERMISSION_LOST
        coverage != ScanCheckpointCoverage.COMPLETE -> ScanIneligibility.PARTIAL_COVERAGE
        !scopeComplete -> ScanIneligibility.SCOPE_INCOMPLETE
        sessionStatus == ScanSessionStatus.PAUSED -> ScanIneligibility.SCAN_PAUSED
        sessionStatus == ScanSessionStatus.CANCELLED -> ScanIneligibility.SCAN_CANCELLED
        sessionStatus == ScanSessionStatus.FAILED || !scanSucceeded -> ScanIneligibility.SCAN_FAILED
        readErrors > 0 -> ScanIneligibility.READ_ERROR
        else -> null
    }

    private fun ObservedLocation.matches(location: ReconciliationLocation): Boolean =
        format == location.format &&
            (hash == location.currentHash || hash == location.originalHash)
}

data class ScanEvidence(
    val sourceId: String,
    val coverage: ScanCheckpointCoverage,
    val scopeComplete: Boolean,
    val sourceReady: Boolean,
    val sessionStatus: ScanSessionStatus,
    val scanSucceeded: Boolean,
    val readErrors: Int,
)

data class ReconciliationLocation(
    val locationId: String,
    val sourceId: String,
    val format: AudioFormat,
    val availability: LocationAvailability,
    val originalHash: FileHash,
    val currentHash: FileHash,
    val excluded: Boolean,
)

data class ObservedLocation(
    val locationId: String,
    val format: AudioFormat,
    val hash: FileHash,
)

data class LocationDecision(
    val locationId: String,
    val availability: LocationAvailability,
)

data class ReconciliationResult(
    val updates: List<LocationDecision>,
    val reason: ScanIneligibility?,
)

enum class ScanIneligibility {
    PARTIAL_COVERAGE,
    SCOPE_INCOMPLETE,
    SCAN_FAILED,
    SCAN_PAUSED,
    SCAN_CANCELLED,
    PERMISSION_LOST,
    READ_ERROR,
}
