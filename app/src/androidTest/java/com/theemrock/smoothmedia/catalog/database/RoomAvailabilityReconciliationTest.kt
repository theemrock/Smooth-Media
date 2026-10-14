package com.theemrock.smoothmedia.catalog.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.catalog.database.entity.CatalogLifecycle
import com.theemrock.smoothmedia.catalog.database.entity.CatalogExclusionEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.LocatorBindingEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateState
import com.theemrock.smoothmedia.catalog.database.entity.ScanCheckpointCoverage
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceScanCheckpointEntity
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackLocationEntity
import com.theemrock.smoothmedia.catalog.identity.AudioFormat as CatalogAudioFormat
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import com.theemrock.smoothmedia.catalog.reconciliation.RoomAvailabilityReconciliation
import com.theemrock.smoothmedia.discovery.AudioDiscoveryResult
import com.theemrock.smoothmedia.discovery.AudioFormat as DiscoveryAudioFormat
import com.theemrock.smoothmedia.discovery.DiscoveredAudioFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomAvailabilityReconciliationTest {
    private lateinit var database: CatalogDatabase
    private lateinit var reconciliation: RoomAvailabilityReconciliation

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        database.librarySourceDao().insert(source())
        reconciliation = RoomAvailabilityReconciliation(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun completeScan_marksUnseenLocationMissing() {
        insertCatalog(locationAvailability = LocationAvailability.AVAILABLE)
        insertSecondCatalogLocation()
        completeScan(observed = false)

        val result = reconciliation.reconcile("scan-1", "source-1", discovery())

        assertEquals(1, result.applied.size)
        assertEquals(LocationAvailability.MISSING, database.trackDao().findLocation("location-1")!!.availability)
    }

    @Test
    fun partialScan_doesNotWriteMissing() {
        insertCatalog(locationAvailability = LocationAvailability.AVAILABLE)
        partialScan()

        val result = reconciliation.reconcile("scan-1", "source-1", discovery())

        assertTrue(result.applied.isEmpty())
        assertEquals(LocationAvailability.AVAILABLE, database.trackDao().findLocation("location-1")!!.availability)
    }

    @Test
    fun rediscoveredLocation_becomesAvailableWithoutChangingIdentity() {
        insertCatalog(locationAvailability = LocationAvailability.MISSING)
        completeScan(observed = true)

        val result = reconciliation.reconcile("scan-1", "source-1", discovery())

        assertEquals(1, result.applied.size)
        assertEquals(LocationAvailability.AVAILABLE, database.trackDao().findLocation("location-1")!!.availability)
        assertEquals("track-1", database.trackDao().findLocation("location-1")!!.trackId)
    }

    @Test
    fun repeatedReconciliation_isIdempotent() {
        insertCatalog(locationAvailability = LocationAvailability.AVAILABLE)
        insertSecondCatalogLocation()
        completeScan(observed = false)

        reconciliation.reconcile("scan-1", "source-1", discovery())
        val second = reconciliation.reconcile("scan-1", "source-1", discovery())

        assertTrue(second.applied.isEmpty())
        assertEquals(LocationAvailability.MISSING, database.trackDao().findLocation("location-1")!!.availability)
    }

    @Test
    fun incompleteCandidateEvidence_doesNotWrite() {
        insertCatalog(locationAvailability = LocationAvailability.AVAILABLE)
        database.scanDao().insertSession(session())
        database.scanDao().insertCheckpoint(checkpoint(ScanCheckpointCoverage.COMPLETE, 1, 1))
        database.scanDao().insertCandidate(candidate(ScanCandidateState.DISCOVERED, null))

        val result = reconciliation.reconcile("scan-1", "source-1", discovery())

        assertTrue(result.applied.isEmpty())
        assertEquals(LocationAvailability.AVAILABLE, database.trackDao().findLocation("location-1")!!.availability)
    }

    @Test
    fun permissionLoss_doesNotWriteMissing() {
        insertCatalog(locationAvailability = LocationAvailability.AVAILABLE)
        database.librarySourceDao().updateState("source-1", 0, SourcePermissionState.LOST, SourceAvailability.PERMISSION_LOST, 3)
        database.scanDao().insertSession(session())
        database.scanDao().insertCheckpoint(checkpoint(ScanCheckpointCoverage.COMPLETE, 1, 1))
        database.scanDao().insertCandidate(candidate(ScanCandidateState.COMPLETED, "location-1"))

        val result = reconciliation.reconcile("scan-1", "source-1", AudioDiscoveryResult.PermissionLost("source-1"))

        assertTrue(result.applied.isEmpty())
        assertEquals(com.theemrock.smoothmedia.catalog.reconciliation.RoomReconciliationBlock.PERMISSION_LOST, result.blocked)
        assertEquals(LocationAvailability.AVAILABLE, database.trackDao().findLocation("location-1")!!.availability)
    }

    @Test
    fun excludedLocation_isNotRestored() {
        insertCatalog(locationAvailability = LocationAvailability.MISSING)
        database.catalogExclusionDao().insert(
            CatalogExclusionEntity("exclusion-1", "source-1", "content://tree/file-1", hash("same"), 1),
        )
        completeScan(observed = true)

        val result = reconciliation.reconcile("scan-1", "source-1", discovery())

        assertTrue(result.applied.isEmpty())
        assertEquals(LocationAvailability.MISSING, database.trackDao().findLocation("location-1")!!.availability)
    }

    private fun completeScan(observed: Boolean) {
        database.scanDao().insertSession(session())
        database.scanDao().insertCheckpoint(checkpoint(ScanCheckpointCoverage.COMPLETE, 1, 1))
        database.scanDao().insertCandidate(candidate(ScanCandidateState.COMPLETED, if (observed) "location-1" else "location-2"))
    }

    private fun partialScan() {
        database.scanDao().insertSession(session())
        database.scanDao().insertCheckpoint(checkpoint(ScanCheckpointCoverage.PARTIAL, 1, 0))
        database.scanDao().insertCandidate(candidate(ScanCandidateState.DISCOVERED, null))
    }

    private fun insertCatalog(locationAvailability: LocationAvailability) {
        database.trackDao().insertTrack(TrackEntity("track-1", CatalogLifecycle.ACTIVE, 1, 1))
        database.trackDao().insertLocation(
            TrackLocationEntity(
                locationId = "location-1",
                trackId = "track-1",
                sourceId = "source-1",
                originalFileHash = hash("same"),
                currentFileHash = hash("same"),
                format = CatalogAudioFormat.MP3,
                availability = locationAvailability,
                path = null,
                mediaStoreId = null,
                fileSizeBytes = 1,
                modifiedAtEpochMs = 1,
                durationMs = null,
                bitrate = null,
                sampleRateHz = null,
                channelCount = null,
                createdAtEpochMs = 1,
                updatedAtEpochMs = 1,
            ),
        )
        database.locatorBindingDao().insert(LocatorBindingEntity("content://tree/file-1", "location-1", "source-1", 1))
    }

    private fun insertSecondCatalogLocation() {
        database.trackDao().insertLocation(
            TrackLocationEntity(
                locationId = "location-2",
                trackId = "track-1",
                sourceId = "source-1",
                originalFileHash = hash("other"),
                currentFileHash = hash("other"),
                format = CatalogAudioFormat.MP3,
                availability = LocationAvailability.AVAILABLE,
                path = null,
                mediaStoreId = null,
                fileSizeBytes = 1,
                modifiedAtEpochMs = 1,
                durationMs = null,
                bitrate = null,
                sampleRateHz = null,
                channelCount = null,
                createdAtEpochMs = 1,
                updatedAtEpochMs = 1,
            ),
        )
        database.locatorBindingDao().insert(LocatorBindingEntity("content://tree/file-2", "location-2", "source-1", 1))
    }

    private fun source() = LibrarySourceEntity(
        "source-1", LibrarySourceType.SAF_TREE, "content://tree/root", null, 1,
        SourcePermissionState.GRANTED, SourceAvailability.AVAILABLE, 1, 1,
    )

    private fun session() = ScanSessionEntity("scan-1", ScanSessionStatus.COMPLETED, 1, 2, 2)

    private fun checkpoint(coverage: ScanCheckpointCoverage, discovered: Int, processed: Int) =
        SourceScanCheckpointEntity("scan-1", "source-1", null, discovered, processed, coverage, 2)

    private fun candidate(state: ScanCandidateState, locationId: String?) =
        ScanCandidateEntity("scan-1", "source-1", "file-1", state, locationId, 2)

    private fun discovery() = AudioDiscoveryResult.Completed("source-1", listOf(
        DiscoveredAudioFile("source-1", "content://tree/file-1", "file-1", "file-1.mp3", DiscoveryAudioFormat.MP3, "audio/mpeg", 1, 1),
    ), 0)

    private fun hash(digest: String) = StoredFileHash("SHA-256", 1, digest)
}
