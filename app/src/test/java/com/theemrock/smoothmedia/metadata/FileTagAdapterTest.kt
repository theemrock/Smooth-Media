package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileTagAdapterTest {
    @Test
    fun picturesWithEqualBytesAreEqual() {
        val first = FileTagPicture(byteArrayOf(1, 2, 3), "image/jpeg", "cover", "Front")
        val second = FileTagPicture(byteArrayOf(1, 2, 3), "image/jpeg", "cover", "Front")

        assertEquals(first, second)
    }

    @Test
    fun changeWithoutPicturesPreservesPictureLayer() {
        val change = FileTagChange(
            properties = mapOf("TITLE" to listOf("Türkçe şarkı")),
        )

        assertEquals(listOf("Türkçe şarkı"), change.properties["TITLE"])
        assertNull(change.pictures)
    }

    @Test
    fun explicitEmptyPicturesMeansRemovePictures() {
        val change = FileTagChange(
            properties = emptyMap(),
            pictures = emptyList(),
        )

        assertEquals(emptyList<FileTagPicture>(), change.pictures)
    }
}
