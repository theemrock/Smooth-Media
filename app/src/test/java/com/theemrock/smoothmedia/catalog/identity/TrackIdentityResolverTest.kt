package com.theemrock.smoothmedia.catalog.identity

import com.theemrock.smoothmedia.catalog.model.FileHash
import com.theemrock.smoothmedia.catalog.model.FileLocator
import com.theemrock.smoothmedia.catalog.model.HashAlgorithmVersion
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import com.theemrock.smoothmedia.catalog.model.TrackId
import com.theemrock.smoothmedia.catalog.model.TrackLocationId
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackIdentityResolverTest {
    @Test
    fun exactCurrentHashMatch_reusesKnownLocation() {
        val location = knownLocation(
            originalHash = hash("original"),
            currentHash = hash("current"),
        )
        val resolver = TrackIdentityResolver(LogicalMatchPolicy { _, _ -> LogicalMatchAssessment.NoMatch })

        val decision = resolver.resolve(
            IdentityInput.FileDiscovered(
                candidate = discoveredFile(hash = hash("current")),
                knownLocations = listOf(location),
            ),
        )

        assertEquals(
            IdentityDecision.ReuseLocation(
                trackId = location.trackId,
                locationId = location.locationId,
                matchedHash = HashMatch.CURRENT,
            ),
            decision,
        )
    }

    @Test
    fun exactOriginalHashMatch_reusesKnownLocation() {
        val location = knownLocation(
            originalHash = hash("original"),
            currentHash = hash("newer-current"),
        )
        val resolver = TrackIdentityResolver(LogicalMatchPolicy { _, _ -> LogicalMatchAssessment.NoMatch })

        val decision = resolver.resolve(
            IdentityInput.FileDiscovered(
                candidate = discoveredFile(hash = hash("original")),
                knownLocations = listOf(location),
            ),
        )

        assertEquals(
            IdentityDecision.ReuseLocation(
                trackId = location.trackId,
                locationId = location.locationId,
                matchedHash = HashMatch.ORIGINAL,
            ),
            decision,
        )
    }

    @Test
    fun sameBytesAtDifferentLocator_createsLocationForExistingTrack() {
        val location = knownLocation(currentHash = hash("same-bytes"))
        val decision = resolver().resolve(
            discoveredInput(
                candidate = discoveredFile(
                    locator = FileLocator("content://music/copy"),
                    hash = hash("same-bytes"),
                ),
                knownLocations = listOf(location),
            ),
        )

        assertEquals(
            IdentityDecision.CreateTrackLocation(
                trackId = location.trackId,
                reason = ExistingTrackReason.EXACT_FILE_HASH,
                matchedLocationId = location.locationId,
            ),
            decision,
        )
    }

    @Test
    fun bothHashesMismatch_createsNewTrack() {
        val decision = resolver().resolve(
            discoveredInput(
                candidate = discoveredFile(hash = hash("new")),
                knownLocations = listOf(
                    knownLocation(
                        originalHash = hash("original"),
                        currentHash = hash("current"),
                    ),
                ),
            ),
        )

        assertEquals(
            IdentityDecision.CreateNewTrack(NewTrackReason.NO_LOGICAL_MATCH),
            decision,
        )
    }

    @Test
    fun sameMetadataWithDifferentHash_doesNotRebindPhysicalIdentity() {
        val existingTrack = TrackId("track-existing")
        val policy = LogicalMatchPolicy { _, _ ->
            LogicalMatchAssessment.HighConfidence(existingTrack)
        }

        val decision = resolver(policy).resolve(
            discoveredInput(
                candidate = discoveredFile(hash = hash("different")),
                knownLocations = listOf(knownLocation(trackId = existingTrack)),
                logicalCandidates = listOf(logicalCandidate(existingTrack)),
            ),
        )

        assertEquals(
            IdentityDecision.ReviewCandidate(
                candidateTrackIds = listOf(existingTrack),
                reason = ReviewReason.HIGH_CONFIDENCE_LOGICAL_MATCH,
            ),
            decision,
        )
    }

    @Test
    fun highConfidenceMp3FlacMatch_isReviewCandidateNotAutomaticMerge() {
        val flacTrack = TrackId("track-flac")
        val policy = LogicalMatchPolicy { candidate, candidates ->
            check(candidate.metadata.format == AudioFormat.MP3)
            check(candidates.single().metadata.format == AudioFormat.FLAC)
            LogicalMatchAssessment.HighConfidence(flacTrack)
        }

        val decision = resolver(policy).resolve(
            discoveredInput(
                candidate = discoveredFile(hash = hash("mp3"), format = AudioFormat.MP3),
                logicalCandidates = listOf(
                    logicalCandidate(flacTrack, format = AudioFormat.FLAC),
                ),
            ),
        )

        assertEquals(
            IdentityDecision.ReviewCandidate(
                candidateTrackIds = listOf(flacTrack),
                reason = ReviewReason.HIGH_CONFIDENCE_LOGICAL_MATCH,
            ),
            decision,
        )
    }

    @Test
    fun ambiguousLogicalMatch_isReviewCandidateWithDeterministicOrder() {
        val first = TrackId("track-a")
        val second = TrackId("track-b")
        val policy = LogicalMatchPolicy { _, _ ->
            LogicalMatchAssessment.Ambiguous(listOf(second, first, second))
        }

        val decision = resolver(policy).resolve(
            discoveredInput(
                candidate = discoveredFile(hash = hash("candidate")),
                logicalCandidates = listOf(logicalCandidate(second), logicalCandidate(first)),
            ),
        )

        assertEquals(
            IdentityDecision.ReviewCandidate(
                candidateTrackIds = listOf(first, second),
                reason = ReviewReason.AMBIGUOUS_LOGICAL_MATCH,
            ),
            decision,
        )
    }

    @Test
    fun differentMastering_createsNewTrack() {
        val policy = LogicalMatchPolicy { _, _ ->
            LogicalMatchAssessment.DifferentRecordingOrMastering
        }

        val decision = resolver(policy).resolve(
            discoveredInput(
                candidate = discoveredFile(hash = hash("remaster")),
                logicalCandidates = listOf(logicalCandidate(TrackId("track-original"))),
            ),
        )

        assertEquals(
            IdentityDecision.CreateNewTrack(NewTrackReason.DIFFERENT_RECORDING_OR_MASTERING),
            decision,
        )
    }

    @Test
    fun priorUserSplitDecision_isNotReversedByScan() {
        val separatedTrack = TrackId("track-separated")
        val policy = LogicalMatchPolicy { _, _ ->
            LogicalMatchAssessment.HighConfidence(separatedTrack)
        }

        val decision = resolver(policy).resolve(
            discoveredInput(
                candidate = discoveredFile(hash = hash("different")),
                logicalCandidates = listOf(logicalCandidate(separatedTrack)),
                priorDecision = PriorLogicalDecision.KeepSeparateFrom(setOf(separatedTrack)),
            ),
        )

        assertEquals(
            IdentityDecision.CreateNewTrack(NewTrackReason.PRIOR_USER_SPLIT),
            decision,
        )
    }

    @Test
    fun priorUserMergeDecision_isNotReversedByScan() {
        val mergedTrack = TrackId("track-user-merged")

        val decision = resolver().resolve(
            discoveredInput(
                candidate = discoveredFile(hash = hash("different")),
                priorDecision = PriorLogicalDecision.MergeInto(mergedTrack),
            ),
        )

        assertEquals(
            IdentityDecision.CreateTrackLocation(
                trackId = mergedTrack,
                reason = ExistingTrackReason.PRIOR_USER_MERGE,
                matchedLocationId = null,
            ),
            decision,
        )
    }

    @Test
    fun sourceUnavailable_preservesTrackAndLocation() {
        val location = knownLocation()

        val decision = resolver().resolve(
            IdentityInput.LocationAvailabilityChanged(
                location = location,
                availability = LocationAvailability.UNAVAILABLE,
            ),
        )

        assertEquals(
            IdentityDecision.PreserveLocation(
                trackId = location.trackId,
                locationId = location.locationId,
                availability = LocationAvailability.UNAVAILABLE,
            ),
            decision,
        )
    }

    @Test
    fun permissionLost_preservesTrackAndLocation() {
        val location = knownLocation()

        val decision = resolver().resolve(
            IdentityInput.LocationAvailabilityChanged(
                location = location,
                availability = LocationAvailability.PERMISSION_LOST,
            ),
        )

        assertEquals(
            IdentityDecision.PreserveLocation(
                trackId = location.trackId,
                locationId = location.locationId,
                availability = LocationAvailability.PERMISSION_LOST,
            ),
            decision,
        )
    }

    @Test
    fun repeatedResolution_isDeterministic() {
        val first = TrackId("track-a")
        val second = TrackId("track-b")
        val policy = LogicalMatchPolicy { _, _ ->
            LogicalMatchAssessment.Ambiguous(listOf(second, first))
        }
        val resolver = resolver(policy)
        val input = discoveredInput(
            candidate = discoveredFile(hash = hash("candidate")),
            logicalCandidates = listOf(logicalCandidate(second), logicalCandidate(first)),
        )

        assertEquals(resolver.resolve(input), resolver.resolve(input))
    }

    private fun resolver(
        policy: LogicalMatchPolicy = LogicalMatchPolicy { _, _ -> LogicalMatchAssessment.NoMatch },
    ) = TrackIdentityResolver(policy)

    private fun discoveredInput(
        candidate: DiscoveredFileIdentity,
        knownLocations: List<KnownTrackLocation> = emptyList(),
        logicalCandidates: List<LogicalTrackCandidate> = emptyList(),
        priorDecision: PriorLogicalDecision? = null,
    ) = IdentityInput.FileDiscovered(
        candidate = candidate,
        knownLocations = knownLocations,
        logicalCandidates = logicalCandidates,
        priorDecision = priorDecision,
    )

    private fun logicalCandidate(
        trackId: TrackId,
        format: AudioFormat = AudioFormat.MP3,
    ) = LogicalTrackCandidate(
        trackId = trackId,
        metadata = metadata(format),
    )

    private fun knownLocation(
        trackId: TrackId = TrackId("track-1"),
        locationId: TrackLocationId = TrackLocationId("location-1"),
        locator: FileLocator = FileLocator("content://music/1"),
        originalHash: FileHash = hash("original"),
        currentHash: FileHash = originalHash,
    ) = KnownTrackLocation(
        trackId = trackId,
        locationId = locationId,
        locator = locator,
        originalFileHash = originalHash,
        currentFileHash = currentHash,
    )

    private fun discoveredFile(
        locator: FileLocator = FileLocator("content://music/1"),
        hash: FileHash,
        format: AudioFormat = AudioFormat.MP3,
    ) = DiscoveredFileIdentity(
        locator = locator,
        fileHash = hash,
        metadata = metadata(format),
    )

    private fun metadata(format: AudioFormat) = LogicalMetadata(
            title = "Track",
            artist = "Artist",
            durationMs = 180_000,
            format = format,
            mastering = null,
    )

    private fun hash(digest: String) = FileHash(
        algorithm = HashAlgorithmVersion(name = "TEST", version = 1),
        digest = digest,
    )
}
