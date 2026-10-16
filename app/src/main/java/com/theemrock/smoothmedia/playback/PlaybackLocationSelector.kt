package com.theemrock.smoothmedia.playback

enum class PlaybackFormat { MP3, FLAC }

data class PlaybackLocation(
    val trackId: String,
    val locationId: String,
    val contentUri: String,
    val format: PlaybackFormat,
    val accessible: Boolean,
)

object PlaybackLocationSelector {
    fun select(locations: List<PlaybackLocation>): PlaybackLocation? = locations
        .asSequence()
        .filter { it.accessible && it.contentUri.startsWith("content://") }
        .sortedWith(compareByDescending<PlaybackLocation> { it.format == PlaybackFormat.FLAC }.thenBy { it.locationId })
        .firstOrNull()
}
