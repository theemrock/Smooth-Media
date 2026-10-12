package com.theemrock.smoothmedia.catalog.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.catalog.database.entity.CatalogLifecycle
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
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.model.FileHash
import com.theemrock.smoothmedia.catalog.model.HashAlgorithmVersion
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import com.theemrock.smoothmedia.catalog.model.TrackLocationId
import com.theemrock.smoothmedia.catalog.repository.CatalogIdentityTransaction
import com.theemrock.smoothmedia.catalog.repository.RoomCatalogRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogIntegrityTest {
    private lateinit var database: CatalogDatabase
    private lateinit var repository: RoomCatalogRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java)
            .addCallback(CatalogDatabaseCallback)
            .build()
        repository = RoomCatalogRepository(database.trackDao(), database.locatorBindingDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun trackInsertAndRead_preservesStableDomainId() {
        val track = track("track-1")

        repository.insertTrack(track)

        assertEquals(track, repository.findTrack("track-1"))
    }

    @Test
    fun locationWithoutTrack_isRejectedByForeignKey() {
        database.librarySourceDao().insert(source())

        assertThrows(SQLiteConstraintException::class.java) {
            database.trackDao().insertLocation(location(trackId = "missing-track"))
        }
    }

    @Test
    fun oneTrack_canOwnMultipleLocations() {
        insertSourceAndTrack()
        database.trackDao().insertLocation(location("location-1"))
        database.trackDao().insertLocation(location("location-2"))

        val result = repository.findTrackWithLocations("track-1")

        assertNotNull(result)
        assertEquals(setOf("location-1", "location-2"), result!!.locations.map { it.locationId }.toSet())
    }

    @Test
    fun sameHashAtDifferentLocators_isAllowed() {
        insertSourceAndTrack()
        val first = location("location-1", digest = "same")
        val second = location("location-2", digest = "same")
        database.trackDao().insertLocation(first)
        database.trackDao().insertLocation(second)

        database.locatorBindingDao().insert(binding("content://music/1", first.locationId))
        database.locatorBindingDao().insert(binding("content://music/2", second.locationId))

        assertEquals(2, database.trackDao().findLocations("track-1").size)
    }

    @Test
    fun duplicateActiveLocatorBinding_isRejected() {
        insertSourceAndTrack()
        val first = location("location-1")
        val second = location("location-2", digest = "other")
        database.trackDao().insertLocation(first)
        database.trackDao().insertLocation(second)
        database.locatorBindingDao().insert(binding("content://music/1", first.locationId))

        assertThrows(SQLiteConstraintException::class.java) {
            database.locatorBindingDao().insert(binding("content://music/1", second.locationId))
        }
    }

    @Test
    fun locatorBindingWithDifferentSourceThanLocation_isRejected() {
        insertSourceAndTrack()
        database.librarySourceDao().insert(source(id = "source-2", rootUri = "content://tree/other"))
        val storedLocation = location("location-1", sourceId = "source-1")
        database.trackDao().insertLocation(storedLocation)

        assertThrows(SQLiteConstraintException::class.java) {
            database.locatorBindingDao().insert(
                binding("content://music/1", storedLocation.locationId, sourceId = "source-2"),
            )
        }
    }

    @Test
    fun originalFileHash_rawUpdateIsRejectedByDatabaseTrigger() {
        insertSourceTrackAndLocation()

        assertThrows(SQLiteConstraintException::class.java) {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE track_locations SET original_hash_digest = 'mutated' WHERE location_id = 'location-1'",
            )
        }

        assertEquals("original", database.trackDao().findLocation("location-1")!!.originalFileHash.digest)
    }

    @Test
    fun currentFileHash_updatesWhenExpectedHashMatches() {
        insertSourceTrackAndLocation()

        val updated = repository.updateCurrentFileHash(
            locationId = TrackLocationId("location-1"),
            expected = fileHash("current"),
            replacement = fileHash("verified-new"),
        )

        val stored = database.trackDao().findLocation("location-1")!!
        assertTrue(updated)
        assertEquals("verified-new", stored.currentFileHash.digest)
        assertEquals("original", stored.originalFileHash.digest)
    }

    @Test
    fun currentFileHash_mismatchDoesNotOverwriteIdentity() {
        insertSourceTrackAndLocation()

        val updated = repository.updateCurrentFileHash(
            locationId = TrackLocationId("location-1"),
            expected = fileHash("wrong"),
            replacement = fileHash("must-not-win"),
        )

        assertFalse(updated)
        assertEquals("current", database.trackDao().findLocation("location-1")!!.currentFileHash.digest)
    }

    @Test
    fun availabilityUpdate_doesNotDeleteTrack() {
        insertSourceTrackAndLocation()

        assertTrue(repository.updateAvailability(TrackLocationId("location-1"), LocationAvailability.UNAVAILABLE))

        assertNotNull(repository.findTrack("track-1"))
        assertEquals(LocationAvailability.UNAVAILABLE, database.trackDao().findLocation("location-1")!!.availability)
    }

    @Test
    fun unavailableAndPermissionLost_remainDistinctStates() {
        insertSourceAndTrack()
        database.trackDao().insertLocation(location("location-1", availability = LocationAvailability.UNAVAILABLE))
        database.trackDao().insertLocation(location("location-2", availability = LocationAvailability.PERMISSION_LOST))

        val states = database.trackDao().findLocations("track-1").associate { it.locationId to it.availability }

        assertEquals(LocationAvailability.UNAVAILABLE, states["location-1"])
        assertEquals(LocationAvailability.PERMISSION_LOST, states["location-2"])
    }

    @Test
    fun identityTransaction_rollsBackTrackAndLocationWhenBindingFails() {
        insertSourceAndTrack()
        database.scanDao().insertSession(
            ScanSessionEntity(
                sessionId = "scan-1",
                status = ScanSessionStatus.RUNNING,
                startedAtEpochMs = 1_000,
                updatedAtEpochMs = 1_000,
                completedAtEpochMs = null,
            ),
        )
        database.scanDao().insertCheckpoint(
            SourceScanCheckpointEntity(
                sessionId = "scan-1",
                sourceId = "source-1",
                continuationToken = null,
                discoveredCount = 1,
                processedCount = 0,
                coverage = ScanCheckpointCoverage.PARTIAL,
                updatedAtEpochMs = 1_000,
            ),
        )
        database.scanDao().insertCandidate(
            ScanCandidateEntity(
                sessionId = "scan-1",
                sourceId = "source-1",
                candidateKey = "candidate-1",
                state = ScanCandidateState.DISCOVERED,
                locationId = null,
                updatedAtEpochMs = 1_000,
            ),
        )
        val occupied = location("occupied")
        database.trackDao().insertLocation(occupied)
        database.locatorBindingDao().insert(binding("content://music/occupied", occupied.locationId))
        val transaction = CatalogIdentityTransaction(database)

        assertThrows(SQLiteConstraintException::class.java) {
            transaction.createTrackWithLocationAndCompleteCandidate(
                track = track("track-new"),
                location = location("location-new", trackId = "track-new"),
                binding = binding("content://music/occupied", "location-new"),
                sessionId = "scan-1",
                sourceId = "source-1",
                candidateKey = "candidate-1",
                completedAtEpochMs = 2_000,
            )
        }

        assertNull(database.trackDao().findTrack("track-new"))
        assertNull(database.trackDao().findLocation("location-new"))
        assertEquals(
            ScanCandidateState.DISCOVERED,
            database.scanDao().findCandidate("scan-1", "source-1", "candidate-1")!!.state,
        )
    }

    @Test
    fun locationWithoutSource_isRejectedByForeignKey() {
        database.trackDao().insertTrack(track())

        assertThrows(SQLiteConstraintException::class.java) {
            database.trackDao().insertLocation(location(sourceId = "missing-source"))
        }
    }

    private fun insertSourceAndTrack() {
        database.librarySourceDao().insert(source())
        database.trackDao().insertTrack(track())
    }

    private fun insertSourceTrackAndLocation() {
        insertSourceAndTrack()
        database.trackDao().insertLocation(
            location(
                originalHash = storedHash("original"),
                currentHash = storedHash("current"),
            ),
        )
    }

    private fun track(id: String = "track-1") = TrackEntity(
        trackId = id,
        catalogLifecycle = CatalogLifecycle.ACTIVE,
        createdAtEpochMs = 1_000,
        updatedAtEpochMs = 1_000,
    )

    private fun source(
        id: String = "source-1",
        rootUri: String = "content://tree/music",
    ) = LibrarySourceEntity(
        sourceId = id,
        sourceType = LibrarySourceType.SAF_TREE,
        rootUri = rootUri,
        volumeName = null,
        persistedPermissionFlags = 3,
        permissionState = SourcePermissionState.GRANTED,
        availability = SourceAvailability.AVAILABLE,
        createdAtEpochMs = 1_000,
        updatedAtEpochMs = 1_000,
    )

    private fun location(
        id: String = "location-1",
        trackId: String = "track-1",
        sourceId: String = "source-1",
        digest: String = "current",
        originalHash: StoredFileHash = storedHash(digest),
        currentHash: StoredFileHash = storedHash(digest),
        availability: LocationAvailability = LocationAvailability.AVAILABLE,
    ) = TrackLocationEntity(
        locationId = id,
        trackId = trackId,
        sourceId = sourceId,
        originalFileHash = originalHash,
        currentFileHash = currentHash,
        format = AudioFormat.FLAC,
        availability = availability,
        path = null,
        mediaStoreId = null,
        fileSizeBytes = 42,
        modifiedAtEpochMs = 900,
        durationMs = 180_000,
        bitrate = 900_000,
        sampleRateHz = 96_000,
        channelCount = 2,
        createdAtEpochMs = 1_000,
        updatedAtEpochMs = 1_000,
    )

    private fun binding(
        locator: String,
        locationId: String,
        sourceId: String = "source-1",
    ) = LocatorBindingEntity(
        locator = locator,
        locationId = locationId,
        sourceId = sourceId,
        boundAtEpochMs = 1_000,
    )

    private fun storedHash(digest: String) = StoredFileHash(
        algorithmName = "SHA-256",
        algorithmVersion = 1,
        digest = digest,
    )

    private fun fileHash(digest: String) = FileHash(
        algorithm = HashAlgorithmVersion("SHA-256", 1),
        digest = digest,
    )
}
