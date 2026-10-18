package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlledMetadataFieldApplyGateTest {
    @Test
    fun dryRun_acceptsAllSupportedFieldsAndKeepsChangesFieldScoped() {
        val gate = ControlledMetadataFieldApplyGate(
            read = { ControlledMetadataFieldCurrent(
                trackId = it,
                sourceId = "source",
                locationId = "location",
                available = true,
                values = mapOf(
                    ControlledMetadataField.TITLE to "old title",
                    ControlledMetadataField.ARTIST to "old artist",
                    ControlledMetadataField.ALBUM to "old album",
                    ControlledMetadataField.ALBUM_ARTIST to "old album artist",
                    ControlledMetadataField.GENRE to "old genre",
                ),
                overriddenFields = emptySet(),
            ) },
            writeAtomically = { true },
        )

        val result = gate.dryRun(listOf(
            review(ControlledMetadataField.TITLE, "old title", "new title"),
            review(ControlledMetadataField.ARTIST, "old artist", "new artist"),
            review(ControlledMetadataField.ALBUM, "old album", "new album"),
            review(ControlledMetadataField.ALBUM_ARTIST, "old album artist", "new album artist"),
            review(ControlledMetadataField.GENRE, "old genre", "new genre"),
        ))

        assertEquals(ControlledMetadataFieldApplyStatus.READY, result.status)
        assertEquals(5, result.changes.size)
        assertTrue(result.changes.all { !it.isNoChange })
    }

    @Test
    fun dryRun_rejectsOverride_staleValue_and_unavailableLocation() {
        val base = ControlledMetadataFieldCurrent(
            trackId = "track",
            sourceId = "source",
            locationId = "location",
            available = true,
            values = mapOf(ControlledMetadataField.TITLE to "old"),
            overriddenFields = emptySet(),
        )
        fun gate(current: ControlledMetadataFieldCurrent) = ControlledMetadataFieldApplyGate(
            read = { current },
            writeAtomically = { error("dry-run must not write") },
        )

        assertEquals(
            ControlledMetadataFieldApplyStatus.REJECTED,
            gate(base.copy(overriddenFields = setOf(ControlledMetadataField.TITLE))).dryRun(
                listOf(review(ControlledMetadataField.TITLE, "old", "new")),
            ).status,
        )
        assertEquals(
            ControlledMetadataFieldApplyStatus.REJECTED,
            gate(base.copy(values = mapOf(ControlledMetadataField.TITLE to "changed"))).dryRun(
                listOf(review(ControlledMetadataField.TITLE, "old", "new")),
            ).status,
        )
        assertEquals(
            ControlledMetadataFieldApplyStatus.REJECTED,
            gate(base.copy(available = false)).dryRun(
                listOf(review(ControlledMetadataField.TITLE, "old", "new")),
            ).status,
        )
    }

    @Test
    fun apply_isIdempotentWhenCurrentValueIsAlreadyNew() {
        var writes = 0
        val gate = ControlledMetadataFieldApplyGate(
            read = { ControlledMetadataFieldCurrent(
                trackId = "track",
                sourceId = "source",
                locationId = "location",
                available = true,
                values = mapOf(ControlledMetadataField.TITLE to "new"),
                overriddenFields = emptySet(),
            ) },
            writeAtomically = { writes++ ; true },
        )

        val result = gate.apply(listOf(review(ControlledMetadataField.TITLE, "old", "new")))

        assertEquals(ControlledMetadataFieldApplyStatus.NO_CHANGE, result.status)
        assertEquals(0, writes)
    }

    @Test
    fun apply_reportsRollbackWhenAtomicWriterRejectsAnyField() {
        val gate = ControlledMetadataFieldApplyGate(
            read = { ControlledMetadataFieldCurrent(
                trackId = "track",
                sourceId = "source",
                locationId = "location",
                available = true,
                values = mapOf(ControlledMetadataField.TITLE to "old"),
                overriddenFields = emptySet(),
            ) },
            writeAtomically = { false },
        )

        val result = gate.apply(listOf(review(ControlledMetadataField.TITLE, "old", "new")))

        assertEquals(ControlledMetadataFieldApplyStatus.ROLLED_BACK, result.status)
        assertTrue(!result.applied)
    }

    private fun review(
        field: ControlledMetadataField,
        old: String,
        new: String,
    ) = ControlledMetadataFieldReview(
        trackId = "track",
        sourceId = "source",
        locationId = "location",
        field = field,
        expectedOldValue = old,
        newValue = new,
        rawEvidence = "verified-byte-evidence",
    )
}
