package com.theemrock.smoothmedia.catalog.repository

import com.theemrock.smoothmedia.catalog.database.entity.LocatorBindingEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackLocationEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackWithLocations
import com.theemrock.smoothmedia.catalog.identity.KnownTrackLocation
import com.theemrock.smoothmedia.catalog.model.FileHash
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import com.theemrock.smoothmedia.catalog.model.TrackLocationId

interface CatalogRepository {
    fun listKnownTrackLocations(): List<KnownTrackLocation>

    fun insertTrack(track: TrackEntity)

    fun findTrack(trackId: String): TrackEntity?

    fun insertLocation(location: TrackLocationEntity)

    fun findLocation(locationId: TrackLocationId): TrackLocationEntity?

    fun bindLocator(binding: LocatorBindingEntity)

    fun findBindingByLocator(locator: String): LocatorBindingEntity?

    fun findTrackWithLocations(trackId: String): TrackWithLocations?

    fun updateCurrentFileHash(
        locationId: TrackLocationId,
        expected: FileHash,
        replacement: FileHash,
    ): Boolean

    fun updateAvailability(
        locationId: TrackLocationId,
        availability: LocationAvailability,
    ): Boolean
}
