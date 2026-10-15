package com.theemrock.smoothmedia.metadata

/**
 * Runs an explicitly requested, bounded metadata batch over existing Track IDs.
 * It deliberately delegates all location, exclusion and write rules to the single-track use case.
 */
class MetadataBatchCoordinator(
    private val enrichTrack: (String) -> MetadataEnrichmentResult,
) {
    fun run(
        trackIds: List<String>,
        cancellationRequested: () -> Boolean = { false },
        onProgress: (MetadataBatchProgress) -> Unit = {},
    ): MetadataBatchResult {
        val uniqueTrackIds = trackIds.distinct()
        require(uniqueTrackIds.size <= MAX_TRACKS) {
            "Metadata batch is limited to $MAX_TRACKS Tracks"
        }

        val outcomes = mutableListOf<MetadataBatchTrackResult>()
        var cancelled = false
        for (trackId in uniqueTrackIds) {
            if (cancellationRequested()) {
                cancelled = true
                break
            }
            val outcome = when (val result = enrichTrack(trackId)) {
                is MetadataEnrichmentResult.Updated ->
                    MetadataBatchTrackResult(trackId, MetadataBatchStatus.SUCCESS, result)

                is MetadataEnrichmentResult.ReadFailed ->
                    MetadataBatchTrackResult(trackId, MetadataBatchStatus.FAILED, result)

                else -> MetadataBatchTrackResult(trackId, MetadataBatchStatus.SKIPPED, result)
            }
            outcomes += outcome
            onProgress(
                MetadataBatchProgress.from(
                    total = uniqueTrackIds.size,
                    outcomes = outcomes,
                    cancelled = false,
                ),
            )
        }
        val progress = MetadataBatchProgress.from(uniqueTrackIds.size, outcomes, cancelled)
        onProgress(progress)
        return MetadataBatchResult(progress = progress, outcomes = outcomes)
    }

    companion object {
        const val MAX_TRACKS = 10
    }
}

enum class MetadataBatchStatus {
    SUCCESS,
    SKIPPED,
    FAILED,
}

data class MetadataBatchTrackResult(
    val trackId: String,
    val status: MetadataBatchStatus,
    val detail: MetadataEnrichmentResult,
)

data class MetadataBatchProgress(
    val total: Int,
    val processed: Int,
    val succeeded: Int,
    val skipped: Int,
    val failed: Int,
    val cancelled: Boolean,
) {
    companion object {
        fun from(
            total: Int,
            outcomes: List<MetadataBatchTrackResult>,
            cancelled: Boolean,
        ) = MetadataBatchProgress(
            total = total,
            processed = outcomes.size,
            succeeded = outcomes.count { it.status == MetadataBatchStatus.SUCCESS },
            skipped = outcomes.count { it.status == MetadataBatchStatus.SKIPPED },
            failed = outcomes.count { it.status == MetadataBatchStatus.FAILED },
            cancelled = cancelled,
        )
    }
}

data class MetadataBatchResult(
    val progress: MetadataBatchProgress,
    val outcomes: List<MetadataBatchTrackResult>,
)
