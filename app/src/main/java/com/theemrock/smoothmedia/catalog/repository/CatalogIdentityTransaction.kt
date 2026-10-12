package com.theemrock.smoothmedia.catalog.repository

import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.LocatorBindingEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateState
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackLocationEntity

class CatalogIdentityTransaction(
    private val database: CatalogDatabase,
) {
    fun createTrackWithLocation(
        track: TrackEntity,
        location: TrackLocationEntity,
        binding: LocatorBindingEntity,
    ) {
        database.runInTransaction {
            database.trackDao().insertTrack(track)
            database.trackDao().insertLocation(location)
            database.locatorBindingDao().insert(binding)
        }
    }

    fun attachLocation(
        location: TrackLocationEntity,
        binding: LocatorBindingEntity,
    ) {
        database.runInTransaction {
            database.trackDao().insertLocation(location)
            database.locatorBindingDao().insert(binding)
        }
    }

    fun createTrackWithLocationAndCompleteCandidate(
        track: TrackEntity,
        location: TrackLocationEntity,
        binding: LocatorBindingEntity,
        sessionId: String,
        sourceId: String,
        candidateKey: String,
        completedAtEpochMs: Long,
    ) {
        database.runInTransaction {
            database.trackDao().insertTrack(track)
            database.trackDao().insertLocation(location)
            database.locatorBindingDao().insert(binding)
            check(
                database.scanDao().updateCandidateState(
                    sessionId = sessionId,
                    sourceId = sourceId,
                    candidateKey = candidateKey,
                    state = ScanCandidateState.COMPLETED,
                    locationId = location.locationId,
                    updatedAtEpochMs = completedAtEpochMs,
                ) == 1,
            )
        }
    }
}
