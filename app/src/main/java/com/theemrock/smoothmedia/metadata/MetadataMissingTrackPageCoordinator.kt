package com.theemrock.smoothmedia.metadata

/**
 * Selects missing-metadata Tracks in bounded keyset pages for an explicit caller.
 * The cursor is returned to the caller only; it is not persisted and never auto-resumed.
 */
class MetadataMissingTrackPageCoordinator(
    private val listMissingTrackIds: (afterTrackId: String?, limit: Int) -> List<String>,
    private val batch: MetadataBatchCoordinator,
) {
    fun runPage(
        afterTrackId: String? = null,
        pageSize: Int = MetadataBatchCoordinator.MAX_TRACKS,
        cancellationRequested: () -> Boolean = { false },
        onProgress: (MetadataBatchProgress) -> Unit = {},
    ): MetadataPageResult {
        require(pageSize in 1..MetadataBatchCoordinator.MAX_TRACKS) {
            "Metadata page is limited to ${MetadataBatchCoordinator.MAX_TRACKS} Tracks"
        }
        val selected = listMissingTrackIds(afterTrackId, pageSize)
            .distinct()
            .take(MetadataBatchCoordinator.MAX_TRACKS)
        if (selected.isEmpty()) {
            val empty = MetadataBatchResult(
                progress = MetadataBatchProgress(
                    total = 0,
                    processed = 0,
                    succeeded = 0,
                    skipped = 0,
                    failed = 0,
                    cancelled = false,
                ),
                outcomes = emptyList(),
            )
            onProgress(empty.progress)
            return MetadataPageResult(empty, nextCursor = null)
        }
        val result = batch.run(selected, cancellationRequested, onProgress)
        return MetadataPageResult(
            batch = result,
            nextCursor = result.outcomes.lastOrNull()?.trackId,
        )
    }
}

data class MetadataPageResult(
    val batch: MetadataBatchResult,
    val nextCursor: String?,
)
