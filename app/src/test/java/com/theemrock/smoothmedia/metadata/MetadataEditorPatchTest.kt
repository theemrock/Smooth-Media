package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataEditorPatchTest {
    @Test
    fun untouchedAndMixedFieldsAreNotIncludedInPatch() {
        val state = MetadataEditorState(selectedTrackIds = listOf("a", "b"))

        val patch = state.toFileTagEdit()

        assertEquals(FileTagEdit(), patch)
    }

    @Test
    fun explicitValueAndClearAreDifferentOperations() {
        val value = MetadataEditorState(selectedTrackIds = listOf("a"))
            .set(MetadataEditorField.ARTIST, "CODEX")
            .toFileTagEdit()
        val clear = MetadataEditorState(selectedTrackIds = listOf("a"))
            .clear(MetadataEditorField.ARTIST)
            .toFileTagEdit()

        assertEquals("CODEX", value.artist)
        assertTrue(value.clearFields.isEmpty())
        assertEquals(setOf("ARTIST"), clear.clearFields)
    }

    @Test
    fun emptyValueRemainsAnExplicitBlankValue() {
        val edit = MetadataEditorState(selectedTrackIds = listOf("a"))
            .set(MetadataEditorField.TITLE, "")
            .toFileTagEdit()

        assertEquals("", edit.title)
        assertTrue(edit.clearFields.isEmpty())
    }
}
