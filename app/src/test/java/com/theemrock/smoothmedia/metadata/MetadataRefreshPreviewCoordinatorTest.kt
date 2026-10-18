package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataRefreshPreviewCoordinatorTest {
    @Test
    fun preview_doesNotWrite_andPreservesUserOverride() {
        val current = snapshot(
            trackId = "track-1",
            title = "ÅžarkÄ±",
            artist = "KullanÄ±cÄ± sanatÃ§Ä±sÄ±",
            overrides = mapOf(MetadataOverrideField.ARTIST to "KullanÄ±cÄ± sanatÃ§Ä±sÄ±"),
        )
        val corrected = MetadataNormalizer().normalize(
            RawAudioMetadata(title = "ÅžarkÄ±", artist = "Yeni SanatÃ§Ä±"),
        )
        val coordinator = MetadataRefreshPreviewCoordinator(
            read = { MetadataReadOnlyResult.Success("track-1", "location-1", RawAudioMetadata(title = "ÅžarkÄ±", artist = "Yeni SanatÃ§Ä±"), corrected) },
            current = { current },
        )

        val result = coordinator.preview(listOf("track-1"))

        assertEquals(1, result.items.size)
        assertTrue(result.items.single().changes.any { it.field == MetadataPreviewField.TITLE })
        assertTrue(result.items.single().changes.none { it.field == MetadataPreviewField.ARTIST })
        assertTrue(result.items.single().rawSummary!!.contains("title="))
        assertTrue(result.items.single().normalizedSummary!!.contains("title="))
    }

    @Test
    fun preview_isBoundedAndIdempotentForUnchangedMetadata() {
        val current = snapshot(trackId = "track-1", title = "Şarkı", artist = "Artist")
        val normalized = MetadataNormalizer().normalize(RawAudioMetadata(title = "Şarkı", artist = "Artist"))
        val coordinator = MetadataRefreshPreviewCoordinator(
            read = { MetadataReadOnlyResult.Success("track-1", "location-1", RawAudioMetadata(title = "Şarkı", artist = "Artist"), normalized) },
            current = { current },
        )

        val first = coordinator.preview(List(25) { "track-1" }, maxItems = 20)
        val second = coordinator.preview(listOf("track-1"), maxItems = 20)

        assertEquals(1, first.items.size)
        assertTrue(first.items.single().changes.isEmpty())
        assertEquals(first, second)
    }

    @Test
    fun apply_isExplicitlyBoundedAndDoesNotRepeatTrackIds() {
        val applied = mutableListOf<String>()
        val coordinator = MetadataRefreshApplyCoordinator { trackId ->
            applied += trackId
            MetadataEnrichmentResult.Updated(trackId, "location-$trackId")
        }

        val result = coordinator.apply(listOf("a", "a", "b"))

        assertEquals(listOf("a", "b"), applied)
        assertEquals(2, result.progress.processed)
        assertEquals(2, result.progress.succeeded)
    }

    @Test
    fun preview_marksInaccessibleWithoutProposedChanges() {
        val coordinator = MetadataRefreshPreviewCoordinator(
            read = { MetadataReadOnlyResult.NoAccessibleLocation },
            current = { error("current metadata must not be read for an inaccessible file") },
        )

        val item = coordinator.preview(listOf("missing-track")).items.single()

        assertEquals(MetadataPreviewStatus.INACCESSIBLE, item.status)
        assertTrue(item.changes.isEmpty())
    }

    @Test
    fun apply_honorsCancellationBeforeAnyCatalogWrite() {
        var calls = 0
        val coordinator = MetadataRefreshApplyCoordinator {
            calls++
            MetadataEnrichmentResult.Updated("track", "location")
        }

        val result = coordinator.apply(listOf("track"), cancellationRequested = { true })

        assertEquals(0, calls)
        assertEquals(0, result.progress.processed)
        assertTrue(result.progress.cancelled)
    }

    private fun snapshot(
        trackId: String,
        title: String,
        artist: String,
        overrides: Map<MetadataOverrideField, String> = emptyMap(),
    ) = TrackMetadataSnapshot(
        trackId = trackId,
        raw = RawAudioMetadata(title = title, artist = artist),
        fileMetadata = NormalizedAudioMetadata(
            title = MetadataField(title, MetadataFieldStatus.AVAILABLE, title),
            artist = MetadataField(artist, MetadataFieldStatus.AVAILABLE, artist),
            album = MetadataField(null, MetadataFieldStatus.MISSING),
            albumArtist = MetadataField(null, MetadataFieldStatus.MISSING),
            genres = MetadataField(null, MetadataFieldStatus.MISSING),
            trackNumber = MetadataField(null, MetadataFieldStatus.MISSING),
            discNumber = MetadataField(null, MetadataFieldStatus.MISSING),
            year = MetadataField(null, MetadataFieldStatus.MISSING),
            durationMs = MetadataField(null, MetadataFieldStatus.MISSING),
        ),
        overrides = overrides,
        effective = NormalizedAudioMetadata(
            title = MetadataField(title, MetadataFieldStatus.AVAILABLE, title),
            artist = MetadataField(overrides[MetadataOverrideField.ARTIST] ?: artist, MetadataFieldStatus.AVAILABLE),
            album = MetadataField(null, MetadataFieldStatus.MISSING),
            albumArtist = MetadataField(null, MetadataFieldStatus.MISSING),
            genres = MetadataField(null, MetadataFieldStatus.MISSING),
            trackNumber = MetadataField(null, MetadataFieldStatus.MISSING),
            discNumber = MetadataField(null, MetadataFieldStatus.MISSING),
            year = MetadataField(null, MetadataFieldStatus.MISSING),
            durationMs = MetadataField(null, MetadataFieldStatus.MISSING),
        ),
    )
}
