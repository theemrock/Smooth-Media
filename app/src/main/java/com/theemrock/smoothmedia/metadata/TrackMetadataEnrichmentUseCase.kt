package com.theemrock.smoothmedia.metadata

import android.net.Uri
import com.theemrock.smoothmedia.catalog.database.dao.CatalogExclusionDao
import com.theemrock.smoothmedia.catalog.database.dao.LocatorBindingDao
import com.theemrock.smoothmedia.catalog.database.dao.TrackDao
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
) {
    fun enrichTrack(trackId: String): MetadataEnrichmentResult {
        if (trackDao.findTrack(trackId) == null) return MetadataEnrichmentResult.TrackNotFound(trackId)

        val locations = trackDao.findLocations(trackId)
            .sortedBy { it.locationId }
            .filter { it.availability == LocationAvailability.AVAILABLE }
        if (locations.isEmpty()) return MetadataEnrichmentResult.NoAccessibleLocation(trackId)

        var excludedCount = 0
        val failures = mutableListOf<MetadataReadFailure>()
        for (location in locations) {
            val binding = bindingDao.findByLocation(location.locationId) ?: continue
            if (exclusionDao.isLocatorExcluded(location.sourceId, binding.locator)) {
                excludedCount++
                continue
            }
            when (val result = reader.read(Uri.parse(binding.locator))) {
                is MetadataReadResult.Success -> {
                    metadata.replaceFileMetadata(
                        trackId = trackId,
                        raw = result.raw,
                        normalized = result.metadata,
                        updatedAtEpochMs = nowEpochMs(),
                    )
                    return MetadataEnrichmentResult.Updated(trackId, location.locationId)
                }
                is MetadataReadResult.Failed -> failures += result.reason
            }
        }
        return when {
            excludedCount == locations.size -> MetadataEnrichmentResult.Excluded(trackId)
            failures.isNotEmpty() -> MetadataEnrichmentResult.ReadFailed(trackId, failures)
            else -> MetadataEnrichmentResult.NoAccessibleLocation(trackId)
        }
    }
}
