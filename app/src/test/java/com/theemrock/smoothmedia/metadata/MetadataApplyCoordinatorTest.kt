package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataApplyCoordinatorTest {
    @Test
    fun dryRun_acceptsOnlyCompletedAuditAutoRepairsInBatches() {
        var writes = 0
        val coordinator = MetadataApplyCoordinator(
            audit = FakeApplyAudit(
                auditStatus = ApplyAuditStatus.COMPLETED,
                auditDecisions = listOf(
                    applyReview("track-1", ControlledMetadataField.TITLE),
                    applyReview("track-2", ControlledMetadataField.ARTIST),
                    ApplyDecisionInput("track-3", "source", "location-3", ControlledMetadataField.TITLE, "old", "new", "READ_ERROR", "evidence"),
                ),
            ),
            gate = ControlledMetadataFieldApplyGate(
                read = { trackId ->
                    val field = if (trackId == "track-1") ControlledMetadataField.TITLE else ControlledMetadataField.ARTIST
                    ControlledMetadataFieldCurrent(trackId, "source", "location-$trackId", true, mapOf(field to "old"), emptySet())
                },
                writeAtomically = { writes++ ; true },
            ),
            applyEnabled = false,
        )

        val result = coordinator.dryRun("audit-1")

        assertEquals(2, result.acceptedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(0, writes)
        assertTrue(result.batches.all { it.size <= 100 })
    }

    private fun applyReview(trackId: String, field: ControlledMetadataField) =
        ApplyDecisionInput(trackId, "source", "location-$trackId", field, "old", "new", "AUTO_REPAIR", "verified")

    private class FakeApplyAudit(
        private val auditStatus: ApplyAuditStatus,
        private val auditDecisions: List<ApplyDecisionInput>,
    ) : MetadataApplyAuditSource {
        override fun status(auditSessionId: String) = auditStatus
        override fun decisions(auditSessionId: String) = auditDecisions
    }
}
