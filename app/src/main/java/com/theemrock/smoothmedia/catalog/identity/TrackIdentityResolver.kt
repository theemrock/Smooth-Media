package com.theemrock.smoothmedia.catalog.identity

import com.theemrock.smoothmedia.catalog.model.TrackId

class TrackIdentityResolver(
    private val logicalMatchPolicy: LogicalMatchPolicy,
) {
    fun resolve(input: IdentityInput): IdentityDecision = when (input) {
        is IdentityInput.FileDiscovered -> resolveFile(input)
        is IdentityInput.LocationAvailabilityChanged -> IdentityDecision.PreserveLocation(
            trackId = input.location.trackId,
            locationId = input.location.locationId,
            availability = input.availability,
        )
    }

    private fun resolveFile(input: IdentityInput.FileDiscovered): IdentityDecision {
        val currentMatch = input.knownLocations.firstOrNull { location ->
            location.locator == input.candidate.locator &&
                location.currentFileHash == input.candidate.fileHash
        }
        if (currentMatch != null) {
            return IdentityDecision.ReuseLocation(
                trackId = currentMatch.trackId,
                locationId = currentMatch.locationId,
                matchedHash = HashMatch.CURRENT,
            )
        }

        val originalMatch = input.knownLocations.firstOrNull { location ->
            location.locator == input.candidate.locator &&
                location.originalFileHash == input.candidate.fileHash
        }
        if (originalMatch != null) {
            return IdentityDecision.ReuseLocation(
                trackId = originalMatch.trackId,
                locationId = originalMatch.locationId,
                matchedHash = HashMatch.ORIGINAL,
            )
        }

        val hashMatches = input.knownLocations.filter { location ->
            location.currentFileHash == input.candidate.fileHash ||
                location.originalFileHash == input.candidate.fileHash
        }
        if (hashMatches.isNotEmpty()) {
            val trackIds = hashMatches.map { it.trackId }.distinctSorted()
            if (trackIds.size > 1) {
                return IdentityDecision.ReviewCandidate(
                    candidateTrackIds = trackIds,
                    reason = ReviewReason.PHYSICAL_HASH_CONFLICT,
                )
            }
            return IdentityDecision.CreateTrackLocation(
                trackId = trackIds.single(),
                reason = ExistingTrackReason.EXACT_FILE_HASH,
                matchedLocationId = hashMatches
                    .sortedBy { it.locationId.value }
                    .first()
                    .locationId,
            )
        }

        when (val priorDecision = input.priorDecision) {
            is PriorLogicalDecision.MergeInto -> {
                return IdentityDecision.CreateTrackLocation(
                    trackId = priorDecision.trackId,
                    reason = ExistingTrackReason.PRIOR_USER_MERGE,
                    matchedLocationId = null,
                )
            }
            is PriorLogicalDecision.KeepSeparateFrom, null -> Unit
        }

        return resolveLogical(input)
    }

    private fun resolveLogical(input: IdentityInput.FileDiscovered): IdentityDecision {
        val assessment = logicalMatchPolicy.assess(input.candidate, input.logicalCandidates)
        val splitIds = (input.priorDecision as? PriorLogicalDecision.KeepSeparateFrom)?.trackIds.orEmpty()

        return when (assessment) {
            LogicalMatchAssessment.NoMatch -> IdentityDecision.CreateNewTrack(
                NewTrackReason.NO_LOGICAL_MATCH,
            )
            LogicalMatchAssessment.DifferentRecordingOrMastering -> IdentityDecision.CreateNewTrack(
                NewTrackReason.DIFFERENT_RECORDING_OR_MASTERING,
            )
            is LogicalMatchAssessment.HighConfidence -> {
                if (assessment.trackId in splitIds) {
                    IdentityDecision.CreateNewTrack(NewTrackReason.PRIOR_USER_SPLIT)
                } else {
                    IdentityDecision.ReviewCandidate(
                        candidateTrackIds = listOf(assessment.trackId),
                        reason = ReviewReason.HIGH_CONFIDENCE_LOGICAL_MATCH,
                    )
                }
            }
            is LogicalMatchAssessment.Ambiguous -> {
                val candidates = assessment.candidateTrackIds
                    .filterNot { it in splitIds }
                    .distinctSorted()
                if (candidates.isEmpty() && splitIds.isNotEmpty()) {
                    IdentityDecision.CreateNewTrack(NewTrackReason.PRIOR_USER_SPLIT)
                } else if (candidates.isEmpty()) {
                    IdentityDecision.CreateNewTrack(NewTrackReason.NO_LOGICAL_MATCH)
                } else {
                    IdentityDecision.ReviewCandidate(
                        candidateTrackIds = candidates,
                        reason = ReviewReason.AMBIGUOUS_LOGICAL_MATCH,
                    )
                }
            }
        }
    }

    private fun List<TrackId>.distinctSorted() =
        distinct().sortedBy { it.value }
}
