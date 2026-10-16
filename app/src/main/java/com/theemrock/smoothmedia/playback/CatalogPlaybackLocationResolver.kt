package com.theemrock.smoothmedia.playback

import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.TrackLocationEntity
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.model.LocationAvailability

class CatalogPlaybackLocationResolver(private val database: CatalogDatabase) {
    fun resolve(trackId: String): List<PlaybackLocation> = database.trackDao().findLocations(trackId)
        .asSequence()
        .filter { it.availability == LocationAvailability.AVAILABLE }
        .mapNotNull { location -> toPlaybackLocation(location) }
        .filterNot { location ->
            val entity = database.trackDao().findLocation(location.locationId) ?: return@filterNot false
            val binding = database.locatorBindingDao().findByLocation(entity.locationId) ?: return@filterNot false
            database.catalogExclusionDao().isLocatorExcluded(entity.sourceId, binding.locator)
        }
        .toList()

    private fun toPlaybackLocation(location: TrackLocationEntity): PlaybackLocation {
        val binding = database.locatorBindingDao().findByLocation(location.locationId)
            ?: return PlaybackLocation(location.trackId, location.locationId, "", PlaybackFormat.MP3, false)
        return PlaybackLocation(
            trackId = location.trackId,
            locationId = location.locationId,
            contentUri = binding.locator,
            format = if (location.format == AudioFormat.FLAC) PlaybackFormat.FLAC else PlaybackFormat.MP3,
            accessible = true,
        )
    }
}
