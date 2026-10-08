package com.theemrock.smoothmedia.p01x

import android.net.Uri

enum class ProbeStatus {
    AVAILABLE,
    MISSING,
    INCOMPLETE,
    UNSUPPORTED,
}

data class ProbeValue<T>(
    val value: T?,
    val status: ProbeStatus,
    val note: String? = null,
)

data class GenreProbe(
    val values: List<String>,
    val status: ProbeStatus,
    val note: String,
) {
    companion object {
        fun fromPlatformValue(rawValue: String?): GenreProbe {
            if (rawValue.isNullOrBlank()) {
                return GenreProbe(
                    values = emptyList(),
                    status = ProbeStatus.MISSING,
                    note = "Platform metadata reader returned no genre value.",
                )
            }

            return GenreProbe(
                values = listOf(rawValue),
                status = ProbeStatus.INCOMPLETE,
                note = "Platform API exposes one combined genre string; separators are preserved without guessing.",
            )
        }
    }
}

data class ArtworkProbe(
    val bytes: ByteArray?,
    val status: ProbeStatus,
    val note: String? = null,
)

data class DiscoveredAudio(
    val mediaStoreId: Long,
    val contentUri: Uri,
    val displayName: String,
    val relativePath: String,
    val mimeType: String?,
    val sizeBytes: Long,
) {
    val formatLabel: String
        get() = when {
            mimeType.equals("audio/flac", ignoreCase = true) ||
                displayName.endsWith(".flac", ignoreCase = true) -> "FLAC"
            else -> "MP3"
        }
}

data class AudioMetadataProbe(
    val title: ProbeValue<String>,
    val artist: ProbeValue<String>,
    val album: ProbeValue<String>,
    val albumArtist: ProbeValue<String>,
    val year: ProbeValue<Int>,
    val trackNumber: ProbeValue<String>,
    val discNumber: ProbeValue<String>,
    val genres: GenreProbe,
    val durationMs: ProbeValue<Long>,
    val bitrate: ProbeValue<Int>,
    val sampleRate: ProbeValue<Int>,
    val channels: ProbeValue<Int>,
    val artwork: ArtworkProbe,
)

object MusicProbePolicy {
    fun isMusicPath(relativePath: String?): Boolean =
        relativePath == "Music/" || relativePath?.startsWith("Music/") == true

    fun isSupportedAudio(mimeType: String?, displayName: String): Boolean =
        mimeType.equals("audio/mpeg", ignoreCase = true) ||
            mimeType.equals("audio/flac", ignoreCase = true) ||
            displayName.endsWith(".mp3", ignoreCase = true) ||
            displayName.endsWith(".flac", ignoreCase = true)
}
