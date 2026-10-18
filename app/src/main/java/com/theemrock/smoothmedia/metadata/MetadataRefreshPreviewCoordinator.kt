package com.theemrock.smoothmedia.metadata


/** A bounded, read-only comparison between stored metadata and a fresh file read. */
class MetadataRefreshPreviewCoordinator(
    private val read: (String) -> MetadataReadOnlyResult,
    private val current: (String) -> TrackMetadataSnapshot?,
) {
    fun preview(trackIds: List<String>, maxItems: Int = DEFAULT_MAX_ITEMS): MetadataRefreshPreviewResult {
        require(maxItems in 1..MAX_ITEMS) { "Metadata preview is limited to $MAX_ITEMS Tracks" }
        val items = trackIds.distinct().take(maxItems).map { trackId ->
            when (val result = read(trackId)) {
                is MetadataReadOnlyResult.Success -> {
                    val stored = current(trackId)
                    MetadataRefreshPreviewItem(
                        trackId = trackId,
                        locationId = result.locationId,
                        status = MetadataPreviewStatus.READ,
                        changes = stored?.let { compare(it, result.metadata) }.orEmpty(),
                        displayTitle = stored?.effective?.title?.value
                            ?: result.metadata.title.value
                            ?: result.raw.title,
                        rawSummary = result.raw.previewSummary(),
                        normalizedSummary = result.metadata.previewSummary(),
                    )
                }
                is MetadataReadOnlyResult.Failed -> MetadataRefreshPreviewItem(trackId, null, MetadataPreviewStatus.READ_FAILED, emptyList(), result.failures.joinToString())
                MetadataReadOnlyResult.NoAccessibleLocation -> MetadataRefreshPreviewItem(trackId, null, MetadataPreviewStatus.INACCESSIBLE, emptyList())
                MetadataReadOnlyResult.Excluded -> MetadataRefreshItemExcluded(trackId)
                MetadataReadOnlyResult.TrackNotFound -> MetadataRefreshPreviewItem(trackId, null, MetadataPreviewStatus.NOT_FOUND, emptyList())
            }
        }
        return MetadataRefreshPreviewResult(items)
    }

    private fun compare(stored: TrackMetadataSnapshot, fresh: NormalizedAudioMetadata): List<MetadataPreviewChange> {
        val effectiveFresh = fresh.withOverrides(stored)
        return listOf(
            change(MetadataPreviewField.TITLE, stored.effective.title.value, effectiveFresh.title.value, fresh.title.status),
            change(MetadataPreviewField.ARTIST, stored.effective.artist.value, effectiveFresh.artist.value, fresh.artist.status),
            change(MetadataPreviewField.ALBUM, stored.effective.album.value, effectiveFresh.album.value, fresh.album.status),
            change(MetadataPreviewField.ALBUM_ARTIST, stored.effective.albumArtist.value, effectiveFresh.albumArtist.value, fresh.albumArtist.status),
            change(MetadataPreviewField.GENRE, stored.effective.genres.value?.joinToString(";"), effectiveFresh.genres.value?.joinToString(";"), fresh.genres.status),
            change(MetadataPreviewField.TRACK_NUMBER, stored.effective.trackNumber.value?.toString(), effectiveFresh.trackNumber.value?.toString(), fresh.trackNumber.status),
            change(MetadataPreviewField.DISC_NUMBER, stored.effective.discNumber.value?.toString(), effectiveFresh.discNumber.value?.toString(), fresh.discNumber.status),
            change(MetadataPreviewField.YEAR, stored.effective.year.value?.toString(), effectiveFresh.year.value?.toString(), fresh.year.status),
            change(MetadataPreviewField.DURATION_MS, stored.effective.durationMs.value?.toString(), effectiveFresh.durationMs.value?.toString(), fresh.durationMs.status),
        ).filterNotNull()
    }

    private fun change(
        field: MetadataPreviewField,
        current: String?,
        proposed: String?,
        status: MetadataFieldStatus,
    ): MetadataPreviewChange? = if (current == proposed || status != MetadataFieldStatus.AVAILABLE) {
        null
    } else {
        MetadataPreviewChange(field, current, proposed, MetadataPreviewConfidence.HIGH)
    }

    private fun NormalizedAudioMetadata.withOverrides(stored: TrackMetadataSnapshot): NormalizedAudioMetadata = copy(
        title = stored.overrides[MetadataOverrideField.TITLE]?.let { stored.effective.title } ?: title,
        artist = stored.overrides[MetadataOverrideField.ARTIST]?.let { stored.effective.artist } ?: artist,
        album = stored.overrides[MetadataOverrideField.ALBUM]?.let { stored.effective.album } ?: album,
        albumArtist = stored.overrides[MetadataOverrideField.ALBUM_ARTIST]?.let { stored.effective.albumArtist } ?: albumArtist,
        genres = stored.overrides[MetadataOverrideField.GENRE]?.let { stored.effective.genres } ?: genres,
        trackNumber = stored.overrides[MetadataOverrideField.TRACK_NUMBER]?.let { stored.effective.trackNumber } ?: trackNumber,
        discNumber = stored.overrides[MetadataOverrideField.DISC_NUMBER]?.let { stored.effective.discNumber } ?: discNumber,
        year = stored.overrides[MetadataOverrideField.YEAR]?.let { stored.effective.year } ?: year,
        durationMs = stored.overrides[MetadataOverrideField.DURATION_MS]?.let { stored.effective.durationMs } ?: durationMs,
    )

    companion object {
        const val DEFAULT_MAX_ITEMS = 20
        const val MAX_ITEMS = 20
    }
}

sealed interface MetadataReadOnlyResult {
    data class Success(
        val trackId: String,
        val locationId: String,
        val raw: RawAudioMetadata,
        val metadata: NormalizedAudioMetadata,
    ) : MetadataReadOnlyResult

    data class Failed(val trackId: String, val failures: List<MetadataReadFailure>) : MetadataReadOnlyResult
    data object NoAccessibleLocation : MetadataReadOnlyResult
    data object Excluded : MetadataReadOnlyResult
    data object TrackNotFound : MetadataReadOnlyResult
}

enum class MetadataPreviewStatus { READ, READ_FAILED, INACCESSIBLE, EXCLUDED, NOT_FOUND }
enum class MetadataPreviewConfidence { HIGH }
enum class MetadataPreviewField { TITLE, ARTIST, ALBUM, ALBUM_ARTIST, GENRE, TRACK_NUMBER, DISC_NUMBER, YEAR, DURATION_MS }

data class MetadataPreviewChange(
    val field: MetadataPreviewField,
    val current: String?,
    val proposed: String?,
    val confidence: MetadataPreviewConfidence,
)

data class MetadataRefreshPreviewItem(
    val trackId: String,
    val locationId: String?,
    val status: MetadataPreviewStatus,
    val changes: List<MetadataPreviewChange>,
    val error: String? = null,
    val displayTitle: String? = null,
    val rawSummary: String? = null,
    val normalizedSummary: String? = null,
)

private fun RawAudioMetadata.previewSummary(): String = listOf(
    "title=${title ?: "∅"}",
    "artist=${artist ?: "∅"}",
    "album=${album ?: "∅"}",
    "albumArtist=${albumArtist ?: "∅"}",
).joinToString(" | ")

private fun NormalizedAudioMetadata.previewSummary(): String = listOf(
    "title=${title.value ?: "∅"}",
    "artist=${artist.value ?: "∅"}",
    "album=${album.value ?: "∅"}",
    "albumArtist=${albumArtist.value ?: "∅"}",
).joinToString(" | ")

private fun MetadataRefreshItemExcluded(trackId: String) = MetadataRefreshPreviewItem(
    trackId = trackId,
    locationId = null,
    status = MetadataPreviewStatus.EXCLUDED,
    changes = emptyList(),
)

data class MetadataRefreshPreviewResult(val items: List<MetadataRefreshPreviewItem>)
