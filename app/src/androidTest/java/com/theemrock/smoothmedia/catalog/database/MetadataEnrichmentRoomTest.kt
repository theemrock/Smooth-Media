package com.theemrock.smoothmedia.catalog.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.catalog.database.entity.CatalogLifecycle
import com.theemrock.smoothmedia.catalog.database.entity.CatalogExclusionEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackLocationEntity
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import com.theemrock.smoothmedia.metadata.AudioMetadataReader
import com.theemrock.smoothmedia.metadata.MetadataEnrichmentResult
import com.theemrock.smoothmedia.metadata.MetadataReadFailure
import com.theemrock.smoothmedia.metadata.MetadataReadResult
import com.theemrock.smoothmedia.metadata.MetadataNormalizer
import com.theemrock.smoothmedia.metadata.RawAudioMetadata
import com.theemrock.smoothmedia.metadata.RoomTrackMetadataRepository
import com.theemrock.smoothmedia.metadata.TrackMetadataEnrichmentUseCase
import android.net.Uri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetadataEnrichmentRoomTest {
    private lateinit var database: CatalogDatabase
    private lateinit var repository: RoomTrackMetadataRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        database.librarySourceDao().insert(source())
        database.trackDao().insertTrack(TrackEntity("track-1", CatalogLifecycle.ACTIVE, 1_000L, 1_000L))
        database.trackDao().insertLocation(location("location-1", LocationAvailability.AVAILABLE))
        database.locatorBindingDao().insert(
            com.theemrock.smoothmedia.catalog.database.entity.LocatorBindingEntity(
                locator = "content://music/one",
                locationId = "location-1",
                sourceId = "source-1",
                boundAtEpochMs = 1_000L,
            ),
        )
        repository = RoomTrackMetadataRepository(database.trackMetadataDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun enrichTrack_readsOneLocationAndPersistsRawAndNormalizedMetadata() {
        val useCase = useCase { MetadataReadResult.Success(raw(), MetadataNormalizer().normalize(raw())) }

        val result = useCase.enrichTrack("track-1")

        assertEquals(MetadataEnrichmentResult.Updated("track-1", "location-1"), result)
        val effective = repository.find("track-1")!!.effective
        assertEquals("Song", effective.title.value)
        assertEquals("Artist", effective.artist.value)
        assertEquals("Album", effective.album.value)
        assertEquals(1000L, effective.durationMs.value)
    }

    @Test
    fun readFailure_preservesExistingMetadataAndOverrides() {
        repository.replaceFileMetadata("track-1", raw("Old"), MetadataNormalizer().normalize(raw("Old")), 2_000L)
        repository.setOverride("track-1", com.theemrock.smoothmedia.metadata.MetadataOverrideField.TITLE, "User", 2_100L)
        val useCase = useCase { MetadataReadResult.Failed(MetadataReadFailure.READ_FAILED) }

        val result = useCase.enrichTrack("track-1")

        assertTrue(result is MetadataEnrichmentResult.ReadFailed)
        assertEquals("User", repository.find("track-1")!!.effective.title.value)
        assertEquals("Old", repository.find("track-1")!!.fileMetadata.title.value)
    }

    @Test
    fun repeatedEnrichment_isIdempotentForTheSelectedLocation() {
        val useCase = useCase { MetadataReadResult.Success(raw(), MetadataNormalizer().normalize(raw())) }
        useCase.enrichTrack("track-1")
        val first = repository.find("track-1")
        useCase.enrichTrack("track-1")
        val second = repository.find("track-1")

        assertEquals(first, second)
        assertEquals(1, database.trackDao().listLocations().size)
    }

    @Test
    fun excludedLocation_isSkippedWithoutWritingMetadata() {
        database.catalogExclusionDao().insert(
            CatalogExclusionEntity(
                exclusionId = "exclusion-1",
                sourceId = "source-1",
                locator = "content://music/one",
                fileHash = StoredFileHash("SHA-256", 1, "digest"),
                createdAtEpochMs = 2_000L,
            ),
        )
        val useCase = useCase { MetadataReadResult.Success(raw(), MetadataNormalizer().normalize(raw())) }

        val result = useCase.enrichTrack("track-1")

        assertEquals(MetadataEnrichmentResult.Excluded("track-1"), result)
        assertEquals(null, repository.find("track-1"))
    }

    private fun useCase(reader: () -> MetadataReadResult) = TrackMetadataEnrichmentUseCase(
        trackDao = database.trackDao(),
        bindingDao = database.locatorBindingDao(),
        exclusionDao = database.catalogExclusionDao(),
        metadata = repository,
        reader = AudioMetadataReader { _: Uri -> reader() },
        nowEpochMs = { 3_000L },
    )

    private fun raw(title: String = "Song") = RawAudioMetadata(
        title = title,
        artist = "Artist",
        album = "Album",
        year = "2024",
        durationMs = "1000",
    )

    private fun source() = LibrarySourceEntity(
        sourceId = "source-1",
        sourceType = LibrarySourceType.SAF_TREE,
        rootUri = "content://tree/music",
        volumeName = null,
        persistedPermissionFlags = 1,
        permissionState = SourcePermissionState.GRANTED,
        availability = SourceAvailability.AVAILABLE,
        createdAtEpochMs = 1_000L,
        updatedAtEpochMs = 1_000L,
    )

    private fun location(id: String, availability: LocationAvailability) = TrackLocationEntity(
        locationId = id,
        trackId = "track-1",
        sourceId = "source-1",
        originalFileHash = StoredFileHash("SHA-256", 1, "digest"),
        currentFileHash = StoredFileHash("SHA-256", 1, "digest"),
        format = com.theemrock.smoothmedia.catalog.identity.AudioFormat.MP3,
        availability = availability,
        path = null,
        mediaStoreId = null,
        fileSizeBytes = 10,
        modifiedAtEpochMs = 900L,
        durationMs = null,
        bitrate = null,
        sampleRateHz = null,
        channelCount = null,
        createdAtEpochMs = 1_000L,
        updatedAtEpochMs = 1_000L,
    )
}
