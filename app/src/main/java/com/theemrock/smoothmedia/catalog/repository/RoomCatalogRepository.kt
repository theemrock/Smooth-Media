package com.theemrock.smoothmedia.catalog.repository

import com.theemrock.smoothmedia.catalog.database.dao.LocatorBindingDao
import com.theemrock.smoothmedia.catalog.database.dao.TrackDao
import com.theemrock.smoothmedia.catalog.database.entity.LocatorBindingEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackLocationEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackWithLocations
import com.theemrock.smoothmedia.catalog.model.FileHash
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import com.theemrock.smoothmedia.catalog.model.TrackLocationId

class RoomCatalogRepository(
    private val trackDao: TrackDao,
    private val locatorBindingDao: LocatorBindingDao,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) : CatalogRepository {
    override fun insertTrack(track: TrackEntity) = trackDao.insertTrack(track)

    override fun findTrack(trackId: String): TrackEntity? = trackDao.findTrack(trackId)

    override fun insertLocation(location: TrackLocationEntity) = trackDao.insertLocation(location)

    override fun findLocation(locationId: TrackLocationId): TrackLocationEntity? =
        trackDao.findLocation(locationId.value)

    override fun bindLocator(binding: LocatorBindingEntity) = locatorBindingDao.insert(binding)

    override fun findBindingByLocator(locator: String): LocatorBindingEntity? =
        locatorBindingDao.findByLocator(locator)

    override fun findTrackWithLocations(trackId: String): TrackWithLocations? =
        trackDao.findTrackWithLocations(trackId)

    override fun updateCurrentFileHash(
        locationId: TrackLocationId,
        expected: FileHash,
        replacement: FileHash,
    ): Boolean = trackDao.updateCurrentHashIfExpected(
        locationId = locationId.value,
        expectedAlgorithmName = expected.algorithm.name,
        expectedAlgorithmVersion = expected.algorithm.version,
        expectedDigest = expected.digest,
        replacementAlgorithmName = replacement.algorithm.name,
        replacementAlgorithmVersion = replacement.algorithm.version,
        replacementDigest = replacement.digest,
        updatedAtEpochMs = nowEpochMs(),
    ) == 1

    override fun updateAvailability(
        locationId: TrackLocationId,
        availability: LocationAvailability,
    ): Boolean = trackDao.updateAvailability(
        locationId = locationId.value,
        availability = availability,
        updatedAtEpochMs = nowEpochMs(),
    ) == 1
}
