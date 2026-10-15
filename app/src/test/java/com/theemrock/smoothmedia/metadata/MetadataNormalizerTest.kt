package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataNormalizerTest {
    private val normalizer = MetadataNormalizer()

    @Test
    fun normalize_readsSupportedFieldsWithoutInventingValues() {
        val result = normalizer.normalize(
            RawAudioMetadata(
                title = "  Song  ",
                artist = "Artist",
                album = "Album",
                albumArtist = "Album Artist",
                genre = "Rock;Metal",
                trackNumber = "03/12",
                discNumber = "2/2",
                year = "2024-01-01",
                durationMs = "12345",
            ),
        )

        assertEquals(MetadataFieldStatus.AVAILABLE, result.title.status)
        assertEquals("Song", result.title.value)
        assertEquals(listOf("Rock;Metal"), result.genres.value)
        assertEquals(3, result.trackNumber.value)
        assertEquals(2, result.discNumber.value)
        assertEquals(2024, result.year.value)
        assertEquals(12345L, result.durationMs.value)
    }

    @Test
    fun normalize_marksBlankAndMalformedValues_explicitly() {
        val result = normalizer.normalize(
            RawAudioMetadata(
                title = " ",
                trackNumber = "side-A",
                discNumber = "",
                year = "unknown",
                durationMs = "-1",
            ),
        )

        assertEquals(MetadataFieldStatus.MISSING, result.title.status)
        assertNull(result.title.value)
        assertEquals(MetadataFieldStatus.INCOMPLETE, result.trackNumber.status)
        assertEquals(MetadataFieldStatus.MISSING, result.discNumber.status)
        assertEquals(MetadataFieldStatus.INCOMPLETE, result.year.status)
        assertEquals(MetadataFieldStatus.INCOMPLETE, result.durationMs.status)
    }

    @Test
    fun unsupportedMetadata_hasNoFabricatedValues() {
        val result = NormalizedAudioMetadata.unsupported("unsupported")

        assertEquals(MetadataFieldStatus.UNSUPPORTED, result.title.status)
        assertNull(result.title.value)
        assertEquals(MetadataFieldStatus.UNSUPPORTED, result.genres.status)
        assertEquals(MetadataFieldStatus.UNSUPPORTED, result.durationMs.status)
    }
}
