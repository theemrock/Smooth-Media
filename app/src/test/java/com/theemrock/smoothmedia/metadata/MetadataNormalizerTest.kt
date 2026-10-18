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

    @Test
    fun normalize_repairsHighConfidenceUtf8Mojibake_andPreservesRawValue() {
        val result = normalizer.normalize(RawAudioMetadata(title = "ÅžarkÄ±"))

        assertEquals("Şarkı", result.title.value)
        assertEquals("ÅžarkÄ±", result.title.rawValue)
    }

    @Test
    fun normalize_preservesValidTurkishAndForeignUnicode() {
        val result = normalizer.normalize(
            RawAudioMetadata(title = "Şarkı — Björk — 東京"),
        )

        assertEquals("Şarkı — Björk — 東京", result.title.value)
    }

    @Test
    fun normalize_preservesAmbiguousText_whenNoRoundTripCandidateExists() {
        val result = normalizer.normalize(RawAudioMetadata(title = "Åland"))

        assertEquals("Åland", result.title.value)
    }

    @Test
    fun normalize_isIdempotent_afterRepair() {
        val first = normalizer.normalize(RawAudioMetadata(title = "GÃ¶zlerin")).title.value
        val second = normalizer.normalize(RawAudioMetadata(title = first)).title.value

        assertEquals("Gözlerin", first)
        assertEquals(first, second)
    }

    @Test
    fun normalizeUserOverride_preservesExactlyWhatTheUserEntered() {
        val result = normalizer.normalizeUserOverride(RawAudioMetadata(title = "ÅžarkÄ±"))

        assertEquals("ÅžarkÄ±", result.title.value)
    }
    @Test
    fun normalize_repairsVerifiedCatalogMojibakeExamples() {
        assertEquals(
            "A\u015fk Yang\u0131n\u0131",
            normalizer.normalize(RawAudioMetadata(title = "A\u00C5\u0178k Yang\u00C4\u00B1n\u00C4\u00B1")).title.value,
        )
        assertEquals(
            "A\u015fk Belas\u0131",
            normalizer.normalize(RawAudioMetadata(title = "A\u00C5\u0178k Belas\u00C4\u00B1")).title.value,
        )
        assertEquals(
            "Adresler Kar\u0131\u015Ft\u0131",
            normalizer.normalize(RawAudioMetadata(title = "Adresler Kar\u00C4\u00B1\u00C5\u0178t\u00C4\u00B1")).title.value,
        )
        assertEquals(
            "Kaf Da\u011F\u0131",
            normalizer.normalize(RawAudioMetadata(title = "Kaf Da\u00C4\u0178\u00C4\u00B1")).title.value,
        )
    }

    @Test
    fun normalize_keepsAmbiguousCatalogCandidateUnchanged() {
        val value = "Yaln\u00C4\u0105z"
        assertEquals(value, normalizer.normalize(RawAudioMetadata(title = value)).title.value)
    }
}
