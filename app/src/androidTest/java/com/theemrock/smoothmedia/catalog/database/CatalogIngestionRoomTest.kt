package com.theemrock.smoothmedia.catalog.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.ingestion.CatalogIdFactory
import com.theemrock.smoothmedia.catalog.ingestion.CatalogIngestionResult
import com.theemrock.smoothmedia.catalog.ingestion.CatalogIngestionUseCase
import com.theemrock.smoothmedia.catalog.identity.LogicalMatchAssessment
import com.theemrock.smoothmedia.catalog.identity.LogicalMatchPolicy
import com.theemrock.smoothmedia.catalog.identity.TrackIdentityResolver
import com.theemrock.smoothmedia.catalog.repository.CatalogIdentityTransaction
import com.theemrock.smoothmedia.catalog.repository.RoomCatalogRepository
import com.theemrock.smoothmedia.catalog.model.TrackId
import com.theemrock.smoothmedia.catalog.model.TrackLocationId
import com.theemrock.smoothmedia.discovery.AudioFormat
import com.theemrock.smoothmedia.discovery.DiscoveredAudioFile
import com.theemrock.smoothmedia.discovery.FullFileHashResult
import com.theemrock.smoothmedia.discovery.HashIdentityAdapter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogIngestionRoomTest {
    private lateinit var database: CatalogDatabase
    private lateinit var useCase: CatalogIngestionUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java)
            .addCallback(CatalogDatabaseCallback)
            .build()
        database.librarySourceDao().insert(source())
        useCase = newUseCase()
    }

    private fun newUseCase(
        resolver: TrackIdentityResolver = TrackIdentityResolver(
            LogicalMatchPolicy { _, _ -> LogicalMatchAssessment.NoMatch },
        ),
    ) = CatalogIngestionUseCase(
            catalog = RoomCatalogRepository(database.trackDao(), database.locatorBindingDao()),
            writer = CatalogIdentityTransaction(database),
            hashIdentityAdapter = HashIdentityAdapter(),
            idFactory = FixedIdFactory,
            nowEpochMs = { 1_000L },
            resolver = resolver,
        )

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun repeatedSameLocatorAndHash_isIdempotentInRoom() {
        val first = useCase.ingest(file("content://provider/document/one"), hash("digest-1"))
        val second = useCase.ingest(file("content://provider/document/one"), hash("digest-1"))

        assertTrue(first is CatalogIngestionResult.Written)
        assertTrue(second is CatalogIngestionResult.Reused)
        assertEquals(1, database.trackDao().findLocations("track-created").size)
    }

    @Test
    fun differentLocatorAndSameHash_createsSecondLocationForExistingTrack() {
        useCase.ingest(file("content://provider/document/one"), hash("digest-1"))
        val result = useCase.ingest(file("content://provider/document/two"), hash("digest-1"))

        assertTrue(result is CatalogIngestionResult.Written)
        assertEquals(2, database.trackDao().findLocations("track-created").size)
    }

    @Test
    fun changedHashAtSameLocator_isWriteBlockedAndLeavesRoomUnchanged() {
        useCase.ingest(file("content://provider/document/one"), hash("digest-1"))
        val result = useCase.ingest(file("content://provider/document/one"), hash("digest-2"))

        assertEquals(CatalogIngestionResult.WriteBlocked("locator-already-bound"), result)
        assertEquals(1, database.trackDao().findLocations("track-created").size)
    }

    @Test
    fun failedHash_doesNotWriteRoom() {
        val result = useCase.ingest(file("content://provider/document/one"), FullFileHashResult.ReadFailed)

        assertEquals(CatalogIngestionResult.HashUnavailable(FullFileHashResult.ReadFailed), result)
        assertEquals(null, database.trackDao().findTrack("track-created"))
    }

    @Test
    fun reviewCandidate_doesNotWriteRoom() {
        val reviewUseCase = newUseCase(
            resolver = TrackIdentityResolver(
                LogicalMatchPolicy { _, _ -> LogicalMatchAssessment.HighConfidence(TrackId("existing")) },
            ),
        )

        val result = reviewUseCase.ingest(file("content://provider/document/one"), hash("digest-1"))

        assertTrue(result is CatalogIngestionResult.NeedsReview)
        assertEquals(null, database.trackDao().findTrack("track-created"))
    }

    private fun source() = LibrarySourceEntity(
        sourceId = "source-1",
        sourceType = LibrarySourceType.SAF_TREE,
        rootUri = "content://provider/tree/music",
        volumeName = null,
        persistedPermissionFlags = 1,
        permissionState = SourcePermissionState.GRANTED,
        availability = SourceAvailability.AVAILABLE,
        createdAtEpochMs = 1_000L,
        updatedAtEpochMs = 1_000L,
    )

    private fun file(uri: String) = DiscoveredAudioFile(
        sourceId = "source-1",
        uri = uri,
        documentId = uri.substringAfterLast('/'),
        displayName = "Track.mp3",
        format = AudioFormat.MP3,
        mimeType = "audio/mpeg",
        sizeBytes = 10,
        modifiedAtEpochMs = 900L,
    )

    private fun hash(digest: String) = FullFileHashResult.Success("SHA-256", 1, digest, 10)

    private object FixedIdFactory : CatalogIdFactory {
        override fun newTrackId() = TrackId("track-created")
        override fun newLocationId() = TrackLocationId("location-${nextId++}")

        private var nextId = 1
    }
}
