package com.theemrock.smoothmedia.catalog.identity

import com.theemrock.smoothmedia.catalog.model.FileHash
import com.theemrock.smoothmedia.catalog.model.FileLocator
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import com.theemrock.smoothmedia.catalog.model.TrackId
import com.theemrock.smoothmedia.catalog.model.TrackLocationId

sealed interface IdentityInput {
    data class FileDiscovered(
        val candidate: DiscoveredFileIdentity,
        val knownLocations: List<KnownTrackLocation>,
        val logicalCandidates: List<LogicalTrackCandidate> = emptyList(),
        val priorDecision: PriorLogicalDecision? = null,
    ) : IdentityInput

    data class LocationAvailabilityChanged(
        val location: KnownTrackLocation,
        val availability: LocationAvailability,
    ) : IdentityInput
}

data class KnownTrackLocation(
    val trackId: TrackId,
    val locationId: TrackLocationId,
    val locator: FileLocator,
    val originalFileHash: FileHash,
    val currentFileHash: FileHash,
)

data class DiscoveredFileIdentity(
    val locator: FileLocator,
    val fileHash: FileHash,
    val metadata: LogicalMetadata,
)

data class LogicalMetadata(
    val title: String?,
    val artist: String?,
    val durationMs: Long?,
    val format: AudioFormat,
    val mastering: String?,
)

data class LogicalTrackCandidate(
    val trackId: TrackId,
    val metadata: LogicalMetadata,
)

sealed interface PriorLogicalDecision {
    data class MergeInto(val trackId: TrackId) : PriorLogicalDecision

    data class KeepSeparateFrom(val trackIds: Set<TrackId>) : PriorLogicalDecision {
        init {
            require(trackIds.isNotEmpty()) { "A split decision must identify at least one Track." }
        }
    }
}

enum class AudioFormat {
    MP3,
    FLAC,
}

fun interface LogicalMatchPolicy {
    fun assess(
        candidate: DiscoveredFileIdentity,
        candidates: List<LogicalTrackCandidate>,
    ): LogicalMatchAssessment
}

sealed interface LogicalMatchAssessment {
    data object NoMatch : LogicalMatchAssessment

    data class HighConfidence(val trackId: TrackId) : LogicalMatchAssessment

    data class Ambiguous(val candidateTrackIds: List<TrackId>) : LogicalMatchAssessment

    data object DifferentRecordingOrMastering : LogicalMatchAssessment
}
