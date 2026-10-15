package com.theemrock.smoothmedia.metadata

import android.net.Uri

enum class MetadataFieldStatus {
    AVAILABLE,
    MISSING,
    INCOMPLETE,
    UNSUPPORTED,
}

data class MetadataField<T>(
    val value: T?,
    val status: MetadataFieldStatus,
    val rawValue: String? = null,
)

data class RawAudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val trackNumber: String? = null,
    val discNumber: String? = null,
    val year: String? = null,
    val durationMs: String? = null,
)

data class NormalizedAudioMetadata(
    val title: MetadataField<String>,
    val artist: MetadataField<String>,
    val album: MetadataField<String>,
    val albumArtist: MetadataField<String>,
    val genres: MetadataField<List<String>>,
    val trackNumber: MetadataField<Int>,
    val discNumber: MetadataField<Int>,
    val year: MetadataField<Int>,
    val durationMs: MetadataField<Long>,
) {
    companion object {
        fun unsupported(note: String) = NormalizedAudioMetadata(
            title = MetadataField(null, MetadataFieldStatus.UNSUPPORTED, note),
            artist = MetadataField(null, MetadataFieldStatus.UNSUPPORTED, note),
            album = MetadataField(null, MetadataFieldStatus.UNSUPPORTED, note),
            albumArtist = MetadataField(null, MetadataFieldStatus.UNSUPPORTED, note),
            genres = MetadataField(null, MetadataFieldStatus.UNSUPPORTED, note),
            trackNumber = MetadataField(null, MetadataFieldStatus.UNSUPPORTED, note),
            discNumber = MetadataField(null, MetadataFieldStatus.UNSUPPORTED, note),
            year = MetadataField(null, MetadataFieldStatus.UNSUPPORTED, note),
            durationMs = MetadataField(null, MetadataFieldStatus.UNSUPPORTED, note),
        )
    }
}

sealed interface MetadataReadResult {
    data class Success(
        val raw: RawAudioMetadata,
        val metadata: NormalizedAudioMetadata,
    ) : MetadataReadResult

    data class Failed(val reason: MetadataReadFailure) : MetadataReadResult
}

enum class MetadataReadFailure {
    PERMISSION_DENIED,
    SOURCE_UNAVAILABLE,
    READ_FAILED,
}

fun interface AudioMetadataReader {
    fun read(uri: Uri): MetadataReadResult
}
