package com.theemrock.smoothmedia.catalog.repository

import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.LocatorBindingEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateState
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackLocationEntity

interface CatalogIdentityWriter {
    fun createTrackWithLocation(
        track: TrackEntity,
        location: TrackLocationEntity,
        binding: LocatorBindingEntity,
    )

    fun attachLocation(
        location: TrackLocationEntity,
        binding: LocatorBindingEntity,
    )
}

class CatalogIdentityTransaction(
    private val database: CatalogDatabase,
    private val onTransactionDurationNanos: (Long) -> Unit = {},
) : CatalogIdentityWriter {
    override fun createTrackWithLocation(
        track: TrackEntity,
        location: TrackLocationEntity,
        binding: LocatorBindingEntity,
    ) {
        measureTransaction {
            database.trackDao().insertTrack(track)
            database.trackDao().insertLocation(location)
            database.locatorBindingDao().insert(binding)
        }
    }

    override fun attachLocation(
        location: TrackLocationEntity,
        binding: LocatorBindingEntity,
    ) {
        measureTransaction {
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
        measureTransaction {
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

    private fun measureTransaction(block: () -> Unit) {
        val startedAt = System.nanoTime()
        try {
            database.runInTransaction(block)
        } finally {
            onTransactionDurationNanos(System.nanoTime() - startedAt)
        }
    }
}
