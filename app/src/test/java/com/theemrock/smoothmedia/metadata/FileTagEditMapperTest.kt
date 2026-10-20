package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class FileTagEditMapperTest {
    @Test
    fun clearFieldsRemovesOnlyExplicitlyClearedTags() {
        val current = FileTagSnapshot(
            properties = mapOf(
                "TITLE" to listOf("Keep title"),
                "COMMENT" to listOf("Remove comment"),
            ),
            pictures = emptyList(),
        )

        val change = FileTagEditMapper.toChange(
            current = current,
            edit = FileTagEdit(clearFields = setOf("COMMENT")),
        )

        assertEquals(listOf("Keep title"), change.properties["TITLE"])
        assertEquals(null, change.properties["COMMENT"])
    }

    @Test
    fun changesOnlyRequestedFieldsAndPreservesUnrelatedProperties() {
        val current = FileTagSnapshot(
            properties = mapOf(
                "TITLE" to listOf("Old"),
                "ARTIST" to listOf("Artist"),
                "CUSTOM" to listOf("keep"),
            ),
            pictures = emptyList(),
        )

        val change = FileTagEditMapper.toChange(
            current = current,
            edit = FileTagEdit(title = "Türkçe başlık"),
        )

        assertEquals(listOf("Türkçe başlık"), change.properties["TITLE"])
        assertEquals(listOf("Artist"), change.properties["ARTIST"])
        assertEquals(listOf("keep"), change.properties["CUSTOM"])
        assertEquals(null, change.pictures)
    }

    @Test
    fun multiGenreEditWritesAllGenreValues() {
        val change = FileTagEditMapper.toChange(
            current = FileTagSnapshot(emptyMap(), emptyList()),
            edit = FileTagEdit(genres = listOf("Rock", "Anadolu Rock")),
        )

        assertEquals(listOf("Rock", "Anadolu Rock"), change.properties["GENRE"])
    }
}
