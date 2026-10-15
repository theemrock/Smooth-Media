package com.theemrock.smoothmedia.metadata

class MetadataNormalizer {
    fun normalize(raw: RawAudioMetadata): NormalizedAudioMetadata = NormalizedAudioMetadata(
        title = text(raw.title),
        artist = text(raw.artist),
        album = text(raw.album),
        albumArtist = text(raw.albumArtist),
        genres = genre(raw.genre),
        trackNumber = sequence(raw.trackNumber),
        discNumber = sequence(raw.discNumber),
        year = year(raw.year),
        durationMs = duration(raw.durationMs),
    )

    private fun text(raw: String?): MetadataField<String> {
        val value = raw?.trim()?.takeIf(String::isNotEmpty)
        return if (value == null) {
            MetadataField(null, MetadataFieldStatus.MISSING, raw)
        } else {
            MetadataField(value, MetadataFieldStatus.AVAILABLE, raw)
        }
    }

    private fun genre(raw: String?): MetadataField<List<String>> {
        val value = raw?.trim()?.takeIf(String::isNotEmpty)
        return if (value == null) {
            MetadataField(null, MetadataFieldStatus.MISSING, raw)
        } else {
            // The platform exposes a combined value; preserve it instead of guessing separators.
            MetadataField(listOf(value), MetadataFieldStatus.AVAILABLE, raw)
        }
    }

    private fun sequence(raw: String?): MetadataField<Int> {
        val value = raw?.trim()?.takeIf(String::isNotEmpty)
            ?: return MetadataField(null, MetadataFieldStatus.MISSING, raw)
        val number = value.substringBefore('/').trim().toIntOrNull()
        return if (number != null && number > 0) {
            MetadataField(number, MetadataFieldStatus.AVAILABLE, raw)
        } else {
            MetadataField(null, MetadataFieldStatus.INCOMPLETE, raw)
        }
    }

    private fun year(raw: String?): MetadataField<Int> {
        val value = raw?.trim()?.takeIf(String::isNotEmpty)
            ?: return MetadataField(null, MetadataFieldStatus.MISSING, raw)
        val parsed = YEAR_PATTERN.find(value)?.value?.toIntOrNull()
        return if (parsed == null) {
            MetadataField(null, MetadataFieldStatus.INCOMPLETE, raw)
        } else {
            MetadataField(parsed, MetadataFieldStatus.AVAILABLE, raw)
        }
    }

    private fun duration(raw: String?): MetadataField<Long> {
        val value = raw?.trim()?.takeIf(String::isNotEmpty)
            ?: return MetadataField(null, MetadataFieldStatus.MISSING, raw)
        val parsed = value.toLongOrNull()
        return if (parsed != null && parsed > 0) {
            MetadataField(parsed, MetadataFieldStatus.AVAILABLE, raw)
        } else {
            MetadataField(null, MetadataFieldStatus.INCOMPLETE, raw)
        }
    }

    private companion object {
        val YEAR_PATTERN = Regex("(?:19|20)\\d{2}")
    }
}
