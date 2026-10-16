package com.theemrock.smoothmedia.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkSelectionTest {
    @Test
    fun trackArtworkRequestsAreBoundToTrackIdentity() {
        assertNotEquals(ArtworkRequest.Track("track-a").cacheKey, ArtworkRequest.Track("track-b").cacheKey)
    }

    @Test
    fun selectsNextCandidateWhenFirstFileHasNoArtwork() {
        val selector = EmbeddedArtworkSelector(
            candidateLoader = { listOf(
                ArtworkCandidate("track-a", "location-a", "content://a"),
                ArtworkCandidate("track-b", "location-b", "content://b"),
            ) },
            artworkReader = { locator -> if (locator == "content://b") byteArrayOf(1, 2, 3) else null },
        )

        assertEquals(byteArrayOf(1, 2, 3).toList(), (selector.load("album-key") ?: byteArrayOf()).toList())
    }

    @Test
    fun cacheIsKeyedByCollectionAndDoesNotCrossAlbums() {
        var reads = 0
        val selector = EmbeddedArtworkSelector(
            candidateLoader = { key -> listOf(ArtworkCandidate(key, key, key)) },
            artworkReader = { locator -> reads += 1; byteArrayOf(locator.length.toByte()) },
        )

        selector.load("album-a")
        selector.load("album-a")
        selector.load("album-b")

        assertEquals(2, reads)
        assertEquals(listOf(7.toByte()), selector.load("album-a")?.toList())
    }

    @Test
    fun emptyOrFailedCandidatesReturnPlaceholderSignal() {
        val selector = EmbeddedArtworkSelector(
            candidateLoader = { emptyList() },
            artworkReader = { error("must not be called") },
        )

        assertNull(selector.load("missing-album"))
    }

    @Test
    fun skipsUnreadableLocationAndUsesAnotherAccessibleLocation() {
        val selector = EmbeddedArtworkSelector(
            candidateLoader = {
                listOf(
                    ArtworkCandidate("track-a", "lost", "content://lost"),
                    ArtworkCandidate("track-a", "available", "content://available"),
                )
            },
            artworkReader = { locator ->
                if (locator == "content://lost") error("permission lost") else byteArrayOf(9)
            },
        )

        assertEquals(listOf(9.toByte()), selector.load("album-key")?.toList())
    }
}
