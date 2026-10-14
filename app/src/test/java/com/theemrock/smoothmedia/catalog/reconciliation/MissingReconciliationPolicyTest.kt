package com.theemrock.smoothmedia.catalog.reconciliation

import com.theemrock.smoothmedia.catalog.database.entity.ScanCheckpointCoverage
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.model.FileHash
import com.theemrock.smoothmedia.catalog.model.HashAlgorithmVersion
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissingReconciliationPolicyTest {
    private val policy = MissingReconciliationPolicy()

    @Test
    fun completeErrorFreeFullScope_marksOnlyUnseenLocationsMissing() {
        val result = policy.evaluate(
            evidence = completeEvidence(),
            locations = listOf(location("loc-1"), location("loc-2")),
            observed = setOf(observed("loc-1")),
        )

        assertEquals(
            listOf(LocationDecision("loc-2", LocationAvailability.MISSING)),
            result.updates,
        )
    }

    @Test
    fun incompleteOrFailedScan_neverProducesMissing() {
        val reasons = listOf(
            ScanIneligibility.PARTIAL_COVERAGE,
            ScanIneligibility.SCOPE_INCOMPLETE,
            ScanIneligibility.SCAN_FAILED,
            ScanIneligibility.SCAN_PAUSED,
            ScanIneligibility.SCAN_CANCELLED,
            ScanIneligibility.PERMISSION_LOST,
            ScanIneligibility.READ_ERROR,
        )

        reasons.forEach { reason ->
            val result = policy.evaluate(
                evidence = completeEvidence(ineligibility = reason),
                locations = listOf(location("loc-1")),
                observed = emptySet(),
            )

            assertTrue("unexpected update for $reason", result.updates.isEmpty())
            assertEquals(reason, result.reason)
        }
    }

    @Test
    fun excludedLocation_isNeverMarkedMissing() {
        val result = policy.evaluate(
            evidence = completeEvidence(),
            locations = listOf(location("loc-1", excluded = true)),
            observed = emptySet(),
        )

        assertTrue(result.updates.isEmpty())
    }

    @Test
    fun exactHashAndFormatReappearance_marksLocationAvailable() {
        val result = policy.evaluate(
            evidence = completeEvidence(),
            locations = listOf(location("loc-1", availability = LocationAvailability.MISSING)),
            observed = setOf(observed("loc-1", hash = hash("same"), format = AudioFormat.MP3)),
        )

        assertEquals(
            listOf(LocationDecision("loc-1", LocationAvailability.AVAILABLE)),
            result.updates,
        )
    }

    @Test
    fun differentFormatDoesNotUseHashToMergeLocations() {
        val result = policy.evaluate(
            evidence = completeEvidence(),
            locations = listOf(location("loc-1", availability = LocationAvailability.MISSING)),
            observed = setOf(observed("loc-1", hash = hash("same"), format = AudioFormat.FLAC)),
        )

        assertTrue(result.updates.isEmpty())
    }

    @Test
    fun repeatedEvaluationIsIdempotent() {
        val evidence = completeEvidence()
        val locations = listOf(location("loc-1"))
        val first = policy.evaluate(evidence, locations, emptySet())
        val second = policy.evaluate(
            evidence,
            locations.map { it.copy(availability = LocationAvailability.MISSING) },
            emptySet(),
        )

        assertEquals(1, first.updates.size)
        assertTrue(second.updates.isEmpty())
    }

    private fun completeEvidence(
        ineligibility: ScanIneligibility? = null,
    ) = ScanEvidence(
        sourceId = "source-1",
        coverage = if (ineligibility == ScanIneligibility.PARTIAL_COVERAGE) {
            ScanCheckpointCoverage.PARTIAL
        } else {
            ScanCheckpointCoverage.COMPLETE
        },
        scopeComplete = ineligibility != ScanIneligibility.SCOPE_INCOMPLETE,
        sourceReady = ineligibility !in setOf(ScanIneligibility.PERMISSION_LOST),
        sessionStatus = when (ineligibility) {
            ScanIneligibility.SCAN_PAUSED -> ScanSessionStatus.PAUSED
            ScanIneligibility.SCAN_CANCELLED -> ScanSessionStatus.CANCELLED
            ScanIneligibility.SCAN_FAILED -> ScanSessionStatus.FAILED
            else -> ScanSessionStatus.COMPLETED
        },
        scanSucceeded = ineligibility !in setOf(
            ScanIneligibility.SCAN_FAILED,
            ScanIneligibility.SCAN_PAUSED,
            ScanIneligibility.SCAN_CANCELLED,
        ),
        readErrors = if (ineligibility == ScanIneligibility.READ_ERROR) 1 else 0,
    )

    private fun location(
        locationId: String,
        availability: LocationAvailability = LocationAvailability.AVAILABLE,
        excluded: Boolean = false,
    ) = ReconciliationLocation(
        locationId = locationId,
        sourceId = "source-1",
        format = AudioFormat.MP3,
        availability = availability,
        originalHash = hash("same"),
        currentHash = hash("same"),
        excluded = excluded,
    )

    private fun observed(
        locationId: String,
        hash: FileHash = hash("same"),
        format: AudioFormat = AudioFormat.MP3,
    ) = ObservedLocation(locationId, format, hash)

    private fun hash(digest: String) = FileHash(HashAlgorithmVersion("SHA-256", 1), digest)
}
