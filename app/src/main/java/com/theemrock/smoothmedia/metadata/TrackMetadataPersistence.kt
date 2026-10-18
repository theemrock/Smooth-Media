package com.theemrock.smoothmedia.metadata

import com.theemrock.smoothmedia.catalog.database.dao.TrackMetadataDao
import com.theemrock.smoothmedia.catalog.database.entity.TrackMetadataEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackMetadataOverrideEntity

enum class MetadataOverrideField {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    GENRE,
    TRACK_NUMBER,
    DISC_NUMBER,
    YEAR,
    DURATION_MS,
}

data class TrackMetadataSnapshot(
    val trackId: String,
    val raw: RawAudioMetadata,
    val fileMetadata: NormalizedAudioMetadata,
    val overrides: Map<MetadataOverrideField, String>,
    val effective: NormalizedAudioMetadata,
)

interface TrackMetadataRepository {
    fun replaceFileMetadata(
        trackId: String,
        raw: RawAudioMetadata,
        normalized: NormalizedAudioMetadata,
        updatedAtEpochMs: Long,
    )

    fun setOverride(
        trackId: String,
        field: MetadataOverrideField,
        value: String,
        updatedAtEpochMs: Long,
    ): Boolean

    fun clearOverride(trackId: String, field: MetadataOverrideField): Boolean

    fun find(trackId: String): TrackMetadataSnapshot?
}

class RoomTrackMetadataRepository(
    private val dao: TrackMetadataDao,
    private val normalizer: MetadataNormalizer = MetadataNormalizer(),
) : TrackMetadataRepository {
    override fun replaceFileMetadata(
        trackId: String,
        raw: RawAudioMetadata,
        normalized: NormalizedAudioMetadata,
        updatedAtEpochMs: Long,
    ) {
        dao.upsert(
            TrackMetadataEntity(
                trackId = trackId,
                rawTitle = raw.title,
                rawArtist = raw.artist,
                rawAlbum = raw.album,
                rawAlbumArtist = raw.albumArtist,
                rawGenre = raw.genre,
                rawTrackNumber = raw.trackNumber,
                rawDiscNumber = raw.discNumber,
                rawYear = raw.year,
                rawDurationMs = raw.durationMs,
                normalizedTitle = normalized.title.value,
                normalizedTitleStatus = normalized.title.status.name,
                normalizedArtist = normalized.artist.value,
                normalizedArtistStatus = normalized.artist.status.name,
                normalizedAlbum = normalized.album.value,
                normalizedAlbumStatus = normalized.album.status.name,
                normalizedAlbumArtist = normalized.albumArtist.value,
                normalizedAlbumArtistStatus = normalized.albumArtist.status.name,
                normalizedGenres = normalized.genres.value?.joinToString(GENRE_SEPARATOR),
                normalizedGenresStatus = normalized.genres.status.name,
                normalizedTrackNumber = normalized.trackNumber.value,
                normalizedTrackNumberStatus = normalized.trackNumber.status.name,
                normalizedDiscNumber = normalized.discNumber.value,
                normalizedDiscNumberStatus = normalized.discNumber.status.name,
                normalizedYear = normalized.year.value,
                normalizedYearStatus = normalized.year.status.name,
                normalizedDurationMs = normalized.durationMs.value,
                normalizedDurationMsStatus = normalized.durationMs.status.name,
                updatedAtEpochMs = updatedAtEpochMs,
            ),
        )
    }

    override fun setOverride(
        trackId: String,
        field: MetadataOverrideField,
        value: String,
        updatedAtEpochMs: Long,
    ): Boolean {
        val normalized = normalizeOverride(field, value) ?: return false
        if (normalized.status != MetadataFieldStatus.AVAILABLE) return false
        dao.upsertOverride(
            TrackMetadataOverrideEntity(
                trackId = trackId,
                fieldName = field.name,
                value = value.trim(),
                updatedAtEpochMs = updatedAtEpochMs,
            ),
        )
        return true
    }

    override fun clearOverride(trackId: String, field: MetadataOverrideField): Boolean =
        dao.clearOverride(trackId, field.name) > 0

    override fun find(trackId: String): TrackMetadataSnapshot? {
        val entity = dao.find(trackId) ?: return null
        val overrides = dao.listOverrides(trackId).mapNotNull { row ->
            runCatching { MetadataOverrideField.valueOf(row.fieldName) }
                .getOrNull()
                ?.let { it to row.value }
        }.toMap()
        val fileMetadata = entity.toNormalized()
        return TrackMetadataSnapshot(
            trackId = trackId,
            raw = entity.toRaw(),
            fileMetadata = fileMetadata,
            overrides = overrides,
            effective = overrides.entries.fold(fileMetadata) { current, (field, value) ->
                applyOverride(current, field, value)
            },
        )
    }

    private fun normalizeOverride(field: MetadataOverrideField, value: String): MetadataField<*>? =
        when (field) {
            MetadataOverrideField.TITLE -> normalizer.normalizeUserOverride(RawAudioMetadata(title = value)).title
            MetadataOverrideField.ARTIST -> normalizer.normalizeUserOverride(RawAudioMetadata(artist = value)).artist
            MetadataOverrideField.ALBUM -> normalizer.normalizeUserOverride(RawAudioMetadata(album = value)).album
            MetadataOverrideField.ALBUM_ARTIST -> normalizer.normalizeUserOverride(RawAudioMetadata(albumArtist = value)).albumArtist
            MetadataOverrideField.GENRE -> normalizer.normalizeUserOverride(RawAudioMetadata(genre = value)).genres
            MetadataOverrideField.TRACK_NUMBER -> normalizer.normalizeUserOverride(RawAudioMetadata(trackNumber = value)).trackNumber
            MetadataOverrideField.DISC_NUMBER -> normalizer.normalizeUserOverride(RawAudioMetadata(discNumber = value)).discNumber
            MetadataOverrideField.YEAR -> normalizer.normalizeUserOverride(RawAudioMetadata(year = value)).year
            MetadataOverrideField.DURATION_MS -> normalizer.normalizeUserOverride(RawAudioMetadata(durationMs = value)).durationMs
        }

    private fun applyOverride(
        metadata: NormalizedAudioMetadata,
        field: MetadataOverrideField,
        value: String,
    ): NormalizedAudioMetadata {
        val raw = when (field) {
            MetadataOverrideField.TITLE -> RawAudioMetadata(title = value)
            MetadataOverrideField.ARTIST -> RawAudioMetadata(artist = value)
            MetadataOverrideField.ALBUM -> RawAudioMetadata(album = value)
            MetadataOverrideField.ALBUM_ARTIST -> RawAudioMetadata(albumArtist = value)
            MetadataOverrideField.GENRE -> RawAudioMetadata(genre = value)
            MetadataOverrideField.TRACK_NUMBER -> RawAudioMetadata(trackNumber = value)
            MetadataOverrideField.DISC_NUMBER -> RawAudioMetadata(discNumber = value)
            MetadataOverrideField.YEAR -> RawAudioMetadata(year = value)
            MetadataOverrideField.DURATION_MS -> RawAudioMetadata(durationMs = value)
        }
        val override = normalizer.normalizeUserOverride(raw)
        return when (field) {
            MetadataOverrideField.TITLE -> metadata.copy(title = override.title)
            MetadataOverrideField.ARTIST -> metadata.copy(artist = override.artist)
            MetadataOverrideField.ALBUM -> metadata.copy(album = override.album)
            MetadataOverrideField.ALBUM_ARTIST -> metadata.copy(albumArtist = override.albumArtist)
            MetadataOverrideField.GENRE -> metadata.copy(genres = override.genres)
            MetadataOverrideField.TRACK_NUMBER -> metadata.copy(trackNumber = override.trackNumber)
            MetadataOverrideField.DISC_NUMBER -> metadata.copy(discNumber = override.discNumber)
            MetadataOverrideField.YEAR -> metadata.copy(year = override.year)
            MetadataOverrideField.DURATION_MS -> metadata.copy(durationMs = override.durationMs)
        }
    }

    private fun TrackMetadataEntity.toRaw() = RawAudioMetadata(
        title = rawTitle,
        artist = rawArtist,
        album = rawAlbum,
        albumArtist = rawAlbumArtist,
        genre = rawGenre,
        trackNumber = rawTrackNumber,
        discNumber = rawDiscNumber,
        year = rawYear,
        durationMs = rawDurationMs,
    )

    private fun TrackMetadataEntity.toNormalized() = NormalizedAudioMetadata(
        title = field(normalizedTitle, normalizedTitleStatus),
        artist = field(normalizedArtist, normalizedArtistStatus),
        album = field(normalizedAlbum, normalizedAlbumStatus),
        albumArtist = field(normalizedAlbumArtist, normalizedAlbumArtistStatus),
        genres = listField(normalizedGenres, normalizedGenresStatus),
        trackNumber = numberField(normalizedTrackNumber, normalizedTrackNumberStatus),
        discNumber = numberField(normalizedDiscNumber, normalizedDiscNumberStatus),
        year = numberField(normalizedYear, normalizedYearStatus),
        durationMs = longField(normalizedDurationMs, normalizedDurationMsStatus),
    )

    private fun field(value: String?, status: String) = MetadataField(value, status.toStatus())

    private fun listField(value: String?, status: String) = MetadataField(
        value?.split(GENRE_SEPARATOR),
        status.toStatus(),
    )

    private fun numberField(value: Int?, status: String) = MetadataField(value, status.toStatus())

    private fun longField(value: Long?, status: String) = MetadataField(value, status.toStatus())

    private fun String.toStatus() = runCatching { MetadataFieldStatus.valueOf(this) }
        .getOrDefault(MetadataFieldStatus.UNSUPPORTED)

    private companion object {
        const val GENRE_SEPARATOR = "\u001F"
    }
}
