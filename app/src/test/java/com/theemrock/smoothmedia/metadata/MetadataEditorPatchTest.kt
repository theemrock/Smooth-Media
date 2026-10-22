package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun prefillUsesOnlyPresentSnapshotFields_andDoesNotMarkStateDirty() {
        val state = MetadataEditorState(selectedTrackIds = listOf("track-1")).prefill(
            FileTagSnapshot(
                properties = mapOf(
                    "TITLE" to listOf("Şarkı"),
                    "ARTIST" to listOf("Björk"),
                    "GENRE" to listOf("Rock", "Ambient"),
                    "DATE" to listOf("2024"),
                ),
                pictures = emptyList(),
            ),
        )

        assertEquals("Şarkı", state.values[MetadataEditorField.TITLE])
        assertEquals("Björk", state.values[MetadataEditorField.ARTIST])
        assertEquals("Rock, Ambient", state.values[MetadataEditorField.GENRE])
        assertTrue(MetadataEditorField.ALBUM !in state.values)
        assertFalse(state.isDirty)
        assertEquals(FileTagEdit(), state.toFileTagEdit())
    }

    @Test
    fun editingPrefilledFieldPatchesOnlyThatField() {
        val state = MetadataEditorState(selectedTrackIds = listOf("track-1"))
            .prefill(
                FileTagSnapshot(
                    properties = mapOf("TITLE" to listOf("Eski"), "ARTIST" to listOf("Mevcut")),
                    pictures = emptyList(),
                ),
            )
            .set(MetadataEditorField.TITLE, "Yeni")

        val patch = state.toFileTagEdit()

        assertEquals("Yeni", patch.title)
        assertEquals(null, patch.artist)
        assertTrue(state.isDirty)
    }

    @Test
    fun latePrefillDoesNotOverwriteUserTouchedField() {
        val state = MetadataEditorState(selectedTrackIds = listOf("track-1"))
            .set(MetadataEditorField.TITLE, "Kullanıcı değeri")
            .prefill(
                FileTagSnapshot(
                    properties = mapOf("TITLE" to listOf("Geç gelen değer"), "ARTIST" to listOf("Sanatçı")),
                    pictures = emptyList(),
                ),
            )

        assertEquals("Kullanıcı değeri", state.values[MetadataEditorField.TITLE])
        assertEquals("Sanatçı", state.values[MetadataEditorField.ARTIST])
        assertTrue(state.isDirty)
        assertEquals("Kullanıcı değeri", state.toFileTagEdit().title)
    }

    @Test
    fun bulkPrefillShowsCommonMixedAndMissingWithoutDirtying() {
        val state = MetadataEditorState(selectedTrackIds = listOf("a", "b")).prefill(
            listOf(
                FileTagSnapshot(
                    properties = mapOf("ARTIST" to listOf("Ortak"), "TITLE" to listOf("Bir")),
                    pictures = emptyList(),
                ),
                FileTagSnapshot(
                    properties = mapOf("ARTIST" to listOf("Ortak"), "TITLE" to listOf("İki")),
                    pictures = emptyList(),
                ),
            ),
        )

        assertEquals("Ortak", state.values[MetadataEditorField.ARTIST])
        assertTrue(MetadataEditorField.TITLE in state.mixed)
        assertTrue(MetadataEditorField.ALBUM in state.missing)
        assertFalse(state.isDirty)
        assertEquals(FileTagEdit(), state.toFileTagEdit())
    }
}
