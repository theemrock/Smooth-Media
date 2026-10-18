package com.theemrock.smoothmedia.metadata

/**
 * Explicit, bounded application step for a previously reviewed preview.
 * It never discovers files, creates catalog rows, hashes locations, or writes source files.
 */
class MetadataRefreshApplyCoordinator(
    private val enrich: (String) -> MetadataEnrichmentResult,
) {
    fun apply(
        trackIds: List<String>,
        cancellationRequested: () -> Boolean = { false },
        onProgress: (MetadataBatchProgress) -> Unit = {},
    ): MetadataRefreshApplyResult {
        val uniqueTrackIds = trackIds.distinct()
        require(uniqueTrackIds.size <= MAX_TRACKS) {
            "Metadata refresh is limited to $MAX_TRACKS Tracks per explicit page"
        }
        val outcomes = mutableListOf<MetadataBatchTrackResult>()
        var cancelled = false
        for (trackId in uniqueTrackIds) {
            if (cancellationRequested()) {
                cancelled = true
                break
            }
            val result = when (val enrichment = enrich(trackId)) {
                is MetadataEnrichmentResult.Updated -> MetadataBatchTrackResult(trackId, MetadataBatchStatus.SUCCESS, enrichment)
                is MetadataEnrichmentResult.ReadFailed -> MetadataBatchTrackResult(trackId, MetadataBatchStatus.FAILED, enrichment)
                else -> MetadataBatchTrackResult(trackId, MetadataBatchStatus.SKIPPED, enrichment)
            }
            outcomes += result
            onProgress(MetadataBatchProgress.from(uniqueTrackIds.size, outcomes, false))
        }
        val progress = MetadataBatchProgress.from(uniqueTrackIds.size, outcomes, cancelled)
        onProgress(progress)
        return MetadataRefreshApplyResult(progress, outcomes)
    }

    companion object {
        const val MAX_TRACKS = 20
    }
}

data class MetadataRefreshApplyResult(
    val progress: MetadataBatchProgress,
    val outcomes: List<MetadataBatchTrackResult>,
)
