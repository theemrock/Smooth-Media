package com.theemrock.smoothmedia.library

import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity

/** Background-loaded source counts; the optional unique total is intentionally separate. */
data class SourceTrackCountSnapshot(
    val bySource: Map<String, Int>,
    val uniqueActiveTrackCount: Int?,
) {
    companion object {
        fun load(
            sources: List<LibrarySourceEntity>,
            countTracksForSource: (String) -> Int,
            countUniqueActiveTracks: (() -> Int)? = null,
        ): SourceTrackCountSnapshot = SourceTrackCountSnapshot(
            bySource = sources.associate { source ->
                source.sourceId to countTracksForSource(source.sourceId)
            },
            uniqueActiveTrackCount = countUniqueActiveTracks?.invoke(),
        )
    }
}
