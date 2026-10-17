package com.theemrock.smoothmedia.lyrics

object LyricsNavigationPolicy {
    fun canApply(requestedTrackId: String, currentTrackId: String?): Boolean =
        currentTrackId != null && requestedTrackId == currentTrackId
}
