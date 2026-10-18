package com.theemrock.smoothmedia.metadata

class MetadataNormalizer {
    fun normalize(raw: RawAudioMetadata): NormalizedAudioMetadata = normalizeInternal(raw, repairMojibake = true)

    fun normalizeUserOverride(raw: RawAudioMetadata): NormalizedAudioMetadata =
        normalizeInternal(raw, repairMojibake = false)

    private fun normalizeInternal(raw: RawAudioMetadata, repairMojibake: Boolean): NormalizedAudioMetadata =
        NormalizedAudioMetadata(
        title = text(raw.title, repairMojibake),
        artist = text(raw.artist, repairMojibake),
        album = text(raw.album, repairMojibake),
        albumArtist = text(raw.albumArtist, repairMojibake),
        genres = genre(raw.genre, repairMojibake),
        trackNumber = sequence(raw.trackNumber),
        discNumber = sequence(raw.discNumber),
        year = year(raw.year),
        durationMs = duration(raw.durationMs),
    )

    private fun text(raw: String?, repairMojibake: Boolean): MetadataField<String> {
        val value = raw?.trim()?.takeIf(String::isNotEmpty)
        return if (value == null) {
            MetadataField(null, MetadataFieldStatus.MISSING, raw)
        } else {
            MetadataField(
                value = if (repairMojibake) TagTextDecoder.normalizeText(value) else value,
                status = MetadataFieldStatus.AVAILABLE,
                rawValue = raw,
            )
        }
    }

    private fun genre(raw: String?, repairMojibake: Boolean): MetadataField<List<String>> {
        val value = raw?.trim()?.takeIf(String::isNotEmpty)
        return if (value == null) {
            MetadataField(null, MetadataFieldStatus.MISSING, raw)
        } else {
            // The platform exposes a combined value; preserve it instead of guessing separators.
            MetadataField(
                value = listOf(if (repairMojibake) TagTextDecoder.normalizeText(value) else value),
                status = MetadataFieldStatus.AVAILABLE,
                rawValue = raw,
            )
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
