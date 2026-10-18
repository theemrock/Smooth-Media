package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlledMetadataApplyGateTest {
    @Test
    fun dryRun_validatesReviewedIdentityAndDoesNotWrite() {
        var writes = 0
        val gate = gate(
            writeAtomically = { writes++ ; true },
        )

        val result = gate.dryRun(listOf(target()))

        assertEquals(ControlledMetadataApplyStatus.READY, result.status)
        assertEquals(0, writes)
        assertEquals("Aşk Yangını", result.changes.single().newTitle)
    }

    @Test
    fun dryRun_rejectsUnexpectedOldTitleWithoutWriting() {
        var writes = 0
        val gate = gate(
            currentTitle = "Başka değer",
            writeAtomically = { writes++ ; true },
        )

        val result = gate.dryRun(listOf(target()))

        assertEquals(ControlledMetadataApplyStatus.REJECTED, result.status)
        assertTrue(result.reason!!.contains("Beklenen eski title"))
        assertEquals(0, writes)
    }

    @Test
    fun dryRun_rejectsWrongSourceLocationOrOverride() {
        val wrongSource = gate(currentSourceId = "wrong-source")
            .dryRun(listOf(target()))
        val wrongLocation = gate(currentLocationId = "wrong-location")
            .dryRun(listOf(target()))
        val overridden = gate(overridePresent = true)
            .dryRun(listOf(target()))

        assertEquals(ControlledMetadataApplyStatus.REJECTED, wrongSource.status)
        assertEquals(ControlledMetadataApplyStatus.REJECTED, wrongLocation.status)
        assertEquals(ControlledMetadataApplyStatus.REJECTED, overridden.status)
    }

    @Test
    fun dryRun_returnsNoChangeForAlreadyCorrectTitle() {
        val result = gate(currentTitle = "Aşk Yangını")
            .dryRun(listOf(target()))

        assertEquals(ControlledMetadataApplyStatus.NO_CHANGE, result.status)
        assertTrue(result.changes.single().isNoChange)
    }

    @Test
    fun apply_isAllOrNothingWhenAtomicWriterFails() {
        var writes = 0
        val gate = gate(writeAtomically = { writes++ ; false })

        val result = gate.apply(listOf(target(), target(trackId = "track-2", locationId = "location-2")))

        assertEquals(ControlledMetadataApplyStatus.ROLLED_BACK, result.status)
        assertEquals(1, writes)
        assertFalse(result.applied)
    }

    @Test
    fun apply_rejectsMoreThanFourReviewedTargets() {
        val gate = gate()
        val result = gate.dryRun(List(5) { index -> target("track-$index", "location-$index") })

        assertEquals(ControlledMetadataApplyStatus.REJECTED, result.status)
        assertTrue(result.reason!!.contains("4"))
    }

    private fun gate(
        currentTitle: String = "AÅk YangÄ±nÄ±",
        currentSourceId: String = "source-verified",
        currentLocationId: String = "location-1",
        overridePresent: Boolean = false,
        writeAtomically: (List<ControlledMetadataTitleChange>) -> Boolean = { true },
    ) = ControlledMetadataApplyGate(
        read = { id ->
            ControlledMetadataCurrent(
                trackId = id,
                sourceId = if (id == "track-1") currentSourceId else "source-verified",
                locationId = if (id == "track-1") currentLocationId else id.replace("track", "location"),
                available = true,
                title = currentTitle,
                titleOverridePresent = overridePresent,
            )
        },
        writeAtomically = writeAtomically,
    )

    private fun target(
        trackId: String = "track-1",
        locationId: String = "location-1",
    ) = ControlledMetadataReview(
        trackId = trackId,
        sourceId = "source-verified",
        locationId = locationId,
        expectedOldTitle = "AÅk YangÄ±nÄ±",
        newTitle = "Aşk Yangını",
        rawEvidence = "verified-id3-utf16",
    )
}
