package com.theemrock.smoothmedia.metadata

import android.net.Uri
import com.theemrock.smoothmedia.catalog.database.dao.CatalogExclusionDao
import com.theemrock.smoothmedia.catalog.database.dao.LocatorBindingDao
import com.theemrock.smoothmedia.catalog.database.dao.TrackDao
import com.theemrock.smoothmedia.genre.RoomGenreRepository
import com.theemrock.smoothmedia.catalog.model.LocationAvailability

sealed interface MetadataEnrichmentResult {
    data class Updated(val trackId: String, val locationId: String) : MetadataEnrichmentResult
    data class ReadFailed(val trackId: String, val failures: List<MetadataReadFailure>) : MetadataEnrichmentResult
    data class NoAccessibleLocation(val trackId: String) : MetadataEnrichmentResult
    data class Excluded(val trackId: String) : MetadataEnrichmentResult
    data class TrackNotFound(val trackId: String) : MetadataEnrichmentResult
}

/** Enriches one explicitly requested Track from one readable, non-excluded Location. */
class TrackMetadataEnrichmentUseCase(
    private val trackDao: TrackDao,
    private val bindingDao: LocatorBindingDao,
    private val exclusionDao: CatalogExclusionDao,
    private val metadata: TrackMetadataRepository,
    private val reader: AudioMetadataReader,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val genreRepository: RoomGenreRepository? = null,
) {
    fun enrichTrack(trackId: String): MetadataEnrichmentResult {
        return when (val result = readTrack(trackId)) {
            is MetadataReadOnlyResult.Success -> {
                metadata.replaceFileMetadata(trackId, result.raw, result.metadata, nowEpochMs())
                genreRepository?.syncMetadataGenres(trackId, result.metadata.genres.value.orEmpty())
                MetadataEnrichmentResult.Updated(trackId, result.locationId)
            }
            is MetadataReadOnlyResult.Failed -> MetadataEnrichmentResult.ReadFailed(trackId, result.failures)
            MetadataReadOnlyResult.NoAccessibleLocation -> MetadataEnrichmentResult.NoAccessibleLocation(trackId)
            MetadataReadOnlyResult.Excluded -> MetadataEnrichmentResult.Excluded(trackId)
            MetadataReadOnlyResult.TrackNotFound -> MetadataEnrichmentResult.TrackNotFound(trackId)
        }
    }

    fun readTrack(trackId: String): MetadataReadOnlyResult {
        if (trackDao.findTrack(trackId) == null) return MetadataReadOnlyResult.TrackNotFound
        val locations = trackDao.findLocations(trackId)
            .sortedBy { it.locationId }
            .filter { it.availability == LocationAvailability.AVAILABLE }
        if (locations.isEmpty()) return MetadataReadOnlyResult.NoAccessibleLocation

        var excludedCount = 0
        val failures = mutableListOf<MetadataReadFailure>()
        for (location in locations) {
            val binding = bindingDao.findByLocation(location.locationId) ?: continue
            if (exclusionDao.isLocatorExcluded(location.sourceId, binding.locator)) {
                excludedCount++
                continue
            }
            when (val result = reader.read(Uri.parse(binding.locator))) {
                is MetadataReadResult.Success -> return MetadataReadOnlyResult.Success(trackId, location.locationId, result.raw, result.metadata)
                is MetadataReadResult.Failed -> failures += result.reason
            }
        }
        return when {
            excludedCount == locations.size -> MetadataReadOnlyResult.Excluded
            failures.isNotEmpty() -> MetadataReadOnlyResult.Failed(trackId, failures)
            else -> MetadataReadOnlyResult.NoAccessibleLocation
        }
    }
}
