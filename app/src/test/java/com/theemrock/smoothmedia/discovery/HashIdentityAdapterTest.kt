package com.theemrock.smoothmedia.discovery

import com.theemrock.smoothmedia.catalog.identity.IdentityDecision
import com.theemrock.smoothmedia.catalog.identity.LogicalMatchPolicy
import com.theemrock.smoothmedia.catalog.identity.TrackIdentityResolver
import com.theemrock.smoothmedia.catalog.model.FileHash
import com.theemrock.smoothmedia.catalog.model.FileLocator
import com.theemrock.smoothmedia.catalog.model.HashAlgorithmVersion
import com.theemrock.smoothmedia.catalog.model.TrackId
import com.theemrock.smoothmedia.catalog.model.TrackLocationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HashIdentityAdapterTest {
    private val adapter = HashIdentityAdapter()

    @Test
    fun successfulHashProducesResolverInputWithoutInventingMetadata() {
        val result = adapter.adapt(file(), success())

        assertTrue(result is HashIdentityAdapterResult.Ready)
        val input = (result as HashIdentityAdapterResult.Ready).input
        assertEquals(FileLocator("content://provider/document/track-1"), input.candidate.locator)
        assertEquals("digest-1", input.candidate.fileHash.digest)
        assertEquals(com.theemrock.smoothmedia.catalog.identity.AudioFormat.MP3, input.candidate.metadata.format)
        assertEquals(null, input.candidate.metadata.title)
        assertEquals(null, input.candidate.metadata.artist)
        assertEquals(null, input.candidate.metadata.durationMs)
        assertEquals(null, input.candidate.metadata.mastering)
    }

    @Test
    fun failedHashDoesNotProduceIdentityInput() {
        val result = adapter.adapt(file(), FullFileHashResult.PermissionLost)

        assertEquals(
            HashIdentityAdapterResult.HashUnavailable(FullFileHashResult.PermissionLost),
            result,
        )
    }

    @Test
    fun missingLocatorIsControlledInputFailure() {
        val result = adapter.adapt(file(uri = ""), success())

        assertEquals(HashIdentityAdapterResult.InvalidInput("locator"), result)
    }

    @Test
    fun sameLocatorAndHash_reusesExistingLocation() {
        val result = resolve(
            fileUri = "content://provider/document/track-1",
            hash = "digest-1",
            knownLocator = "content://provider/document/track-1",
            knownHash = "digest-1",
        )

        assertEquals(
            IdentityDecision.ReuseLocation(
                trackId = TrackId("track-1"),
                locationId = TrackLocationId("location-1"),
                matchedHash = com.theemrock.smoothmedia.catalog.identity.HashMatch.CURRENT,
            ),
            result,
        )
    }

    @Test
    fun differentLocatorAndSameHash_attachesLocationToExistingTrack() {
        val result = resolve(
            fileUri = "content://provider/document/moved-track",
            hash = "digest-1",
            knownLocator = "content://provider/document/track-1",
            knownHash = "digest-1",
        )

        assertEquals(
            IdentityDecision.CreateTrackLocation(
                trackId = TrackId("track-1"),
                reason = com.theemrock.smoothmedia.catalog.identity.ExistingTrackReason.EXACT_FILE_HASH,
                matchedLocationId = TrackLocationId("location-1"),
            ),
            result,
        )
    }

    @Test
    fun sameLocatorAndChangedHash_doesNotReusePhysicalIdentity() {
        val result = resolve(
            fileUri = "content://provider/document/track-1",
            hash = "digest-new",
            knownLocator = "content://provider/document/track-1",
            knownHash = "digest-1",
        )

        assertEquals(
            IdentityDecision.CreateNewTrack(
                com.theemrock.smoothmedia.catalog.identity.NewTrackReason.NO_LOGICAL_MATCH,
            ),
            result,
        )
    }

    @Test
    fun hashVersionAndByteCountDoNotChangeIdentityInput() {
        val first = adapter.adapt(file(), success(bytesRead = 12))
        val second = adapter.adapt(file(), success(bytesRead = 12_345))

        assertEquals(first, second)
    }

    @Test
    fun unsupportedHashAlgorithmIsControlledFailure() {
        val result = adapter.adapt(
            file(),
            FullFileHashResult.Success("SHA-1", 1, "digest-1", 10),
        )

        assertEquals(HashIdentityAdapterResult.UnsupportedHash("SHA-1", 1), result)
    }

    private fun resolve(
        fileUri: String,
        hash: String,
        knownLocator: String,
        knownHash: String,
    ): IdentityDecision {
        val adapted = adapter.adapt(
            file(uri = fileUri),
            success(digest = hash),
            knownLocations = listOf(
                com.theemrock.smoothmedia.catalog.identity.KnownTrackLocation(
                    trackId = TrackId("track-1"),
                    locationId = TrackLocationId("location-1"),
                    locator = FileLocator(knownLocator),
                    originalFileHash = fileHash(knownHash),
                    currentFileHash = fileHash(knownHash),
                ),
            ),
        ) as HashIdentityAdapterResult.Ready
        return TrackIdentityResolver(
            LogicalMatchPolicy { _, _ ->
                com.theemrock.smoothmedia.catalog.identity.LogicalMatchAssessment.NoMatch
            },
        ).resolve(adapted.input)
    }

    private fun file(uri: String = "content://provider/document/track-1") = DiscoveredAudioFile(
        sourceId = "source-1",
        uri = uri,
        documentId = "track-1",
        displayName = "Track.mp3",
        format = AudioFormat.MP3,
        mimeType = "audio/mpeg",
        sizeBytes = 10,
        modifiedAtEpochMs = 20,
    )

    private fun success(
        digest: String = "digest-1",
        bytesRead: Long = 10,
    ) = FullFileHashResult.Success("SHA-256", 1, digest, bytesRead)

    private fun fileHash(digest: String) = FileHash(
        HashAlgorithmVersion("SHA-256", 1),
        digest,
    )
}
