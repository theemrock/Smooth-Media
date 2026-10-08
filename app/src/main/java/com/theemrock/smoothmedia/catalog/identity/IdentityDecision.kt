package com.theemrock.smoothmedia.catalog.identity

import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import com.theemrock.smoothmedia.catalog.model.TrackId
import com.theemrock.smoothmedia.catalog.model.TrackLocationId

sealed interface IdentityDecision {
    data class ReuseLocation(
        val trackId: TrackId,
        val locationId: TrackLocationId,
        val matchedHash: HashMatch,
    ) : IdentityDecision

    data class CreateTrackLocation(
        val trackId: TrackId,
        val reason: ExistingTrackReason,
        val matchedLocationId: TrackLocationId?,
    ) : IdentityDecision

    data class CreateNewTrack(
        val reason: NewTrackReason,
    ) : IdentityDecision

    data class ReviewCandidate(
        val candidateTrackIds: List<TrackId>,
        val reason: ReviewReason,
    ) : IdentityDecision {
        init {
            require(candidateTrackIds.isNotEmpty()) { "A review decision must identify a Track." }
        }
    }

    data class PreserveLocation(
        val trackId: TrackId,
        val locationId: TrackLocationId,
        val availability: LocationAvailability,
    ) : IdentityDecision
}

enum class HashMatch {
    CURRENT,
    ORIGINAL,
}

enum class ExistingTrackReason {
    EXACT_FILE_HASH,
    PRIOR_USER_MERGE,
}

enum class NewTrackReason {
    NO_LOGICAL_MATCH,
    DIFFERENT_RECORDING_OR_MASTERING,
    PRIOR_USER_SPLIT,
}

enum class ReviewReason {
    HIGH_CONFIDENCE_LOGICAL_MATCH,
    AMBIGUOUS_LOGICAL_MATCH,
    PHYSICAL_HASH_CONFLICT,
}
