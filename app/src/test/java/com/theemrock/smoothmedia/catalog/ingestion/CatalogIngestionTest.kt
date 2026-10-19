package com.theemrock.smoothmedia.catalog.ingestion

import com.theemrock.smoothmedia.catalog.database.entity.CatalogLifecycle
import com.theemrock.smoothmedia.catalog.database.entity.LocatorBindingEntity
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackLocationEntity
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.identity.KnownTrackLocation
import com.theemrock.smoothmedia.catalog.model.FileHash
import com.theemrock.smoothmedia.catalog.model.FileLocator
import com.theemrock.smoothmedia.catalog.model.HashAlgorithmVersion
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import com.theemrock.smoothmedia.catalog.model.TrackId
import com.theemrock.smoothmedia.catalog.model.TrackLocationId
import com.theemrock.smoothmedia.catalog.repository.CatalogRepository
import com.theemrock.smoothmedia.catalog.repository.CatalogIdentityWriter
import com.theemrock.smoothmedia.discovery.AudioDiscoveryResult
import com.theemrock.smoothmedia.discovery.AudioFormat as DiscoveredAudioFormat
import com.theemrock.smoothmedia.discovery.DiscoveredAudioFile
import com.theemrock.smoothmedia.discovery.FullFileHashResult
import com.theemrock.smoothmedia.discovery.HashIdentityAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogIngestionTest {
    @Test
    fun sameLocatorAndHash_isIdempotentWithoutWrite() {
        val existing = knownLocation("content://track/1", "hash-1")
        val writer = RecordingWriter()

        val result = useCase(listOf(existing), writer).ingest(file(), hash("hash-1"))

        assertTrue(result is CatalogIngestionResult.Reused)
        assertEquals(0, writer.writes)
    }

    @Test
    fun newFile_createsTrackLocationAndBindingAtomically() {
        val writer = RecordingWriter()

        val result = useCase(emptyList(), writer).ingest(file(), hash("hash-new"))

        assertTrue(result is CatalogIngestionResult.Written)
        assertEquals(1, writer.writes)
        assertTrue(writer.createdTrack != null)
        assertTrue(writer.createdLocation != null)
        assertEquals("content://track/1", writer.createdBinding?.locator)
    }

    @Test
    fun movedFileWithSameHash_attachesNewLocationToExistingTrack() {
        val existing = knownLocation("content://track/old", "hash-1")
        val writer = RecordingWriter()

        val result = useCase(listOf(existing), writer).ingest(
            file(uri = "content://track/new"),
            hash("hash-1"),
        )

        assertTrue(result is CatalogIngestionResult.Written)
        assertEquals(1, writer.writes)
        assertEquals("track-1", writer.createdLocation?.trackId)
    }

    @Test
    fun reviewCandidate_neverWritesOrMerges() {
        val first = knownLocation("content://track/1", "hash-same", trackId = "track-a", locationId = "location-a")
        val second = knownLocation("content://track/2", "hash-same", trackId = "track-b", locationId = "location-b")
        val writer = RecordingWriter()

        val result = useCase(listOf(first, second), writer).ingest(
            file(uri = "content://track/new"),
            hash("hash-same"),
        )

        assertTrue(result is CatalogIngestionResult.NeedsReview)
        assertEquals(0, writer.writes)
    }

    @Test
    fun failedHash_neverWritesCatalog() {
        val writer = RecordingWriter()

        val result = useCase(emptyList(), writer).ingest(file(), FullFileHashResult.ReadFailed)

        assertEquals(CatalogIngestionResult.HashUnavailable(FullFileHashResult.ReadFailed), result)
        assertEquals(0, writer.writes)
    }

    @Test
    fun missingFileFacts_areControlledInputFailure() {
        val writer = RecordingWriter()

        val result = useCase(emptyList(), writer).ingest(
            file(sizeBytes = null),
            hash("hash-new"),
        )

        assertEquals(CatalogIngestionResult.InvalidInput("sizeBytes"), result)
        assertEquals(0, writer.writes)
    }

    @Test
    fun changedHashAtExistingLocator_isBlockedWithoutOverwritingLocation() {
        val existing = knownLocation("content://track/1", "hash-old")
        val writer = RecordingWriter()

        val result = useCase(listOf(existing), writer).ingest(file(), hash("hash-new"))

        assertEquals(CatalogIngestionResult.WriteBlocked("locator-already-bound"), result)
        assertEquals(0, writer.writes)
    }

    @Test
    fun scanKnownLocationCache_isReusedAndUpdatedAfterWrite() {
        val catalog = FakeCatalogRepository(emptyList())
        val writer = RecordingWriter()
        val cachedLocations = mutableListOf<KnownTrackLocation>()
        val useCase = CatalogIngestionUseCase(
            catalog = catalog,
            writer = writer,
            hashIdentityAdapter = HashIdentityAdapter(),
            idFactory = FixedIdFactory,
            nowEpochMs = { 1_000L },
            knownLocationsCache = cachedLocations,
        )

        val first = useCase.ingest(file(uri = "content://track/new-1"), hash("hash-new"))
        val second = useCase.ingest(file(uri = "content://track/new-2"), hash("hash-new"))

        assertTrue(first is CatalogIngestionResult.Written)
        assertTrue(second is CatalogIngestionResult.Written)
        assertEquals(2, writer.writes)
        assertEquals(0, catalog.listKnownTrackLocationsCalls)
        assertEquals("track-created", writer.createdLocation?.trackId)
        assertEquals(2, cachedLocations.size)
    }

    private fun useCase(
        knownLocations: List<KnownTrackLocation>,
        writer: RecordingWriter,
    ) = CatalogIngestionUseCase(
        catalog = FakeCatalogRepository(knownLocations),
        writer = writer,
        hashIdentityAdapter = HashIdentityAdapter(),
        idFactory = FixedIdFactory,
        nowEpochMs = { 1_000L },
    )

    private fun file(
        uri: String = "content://track/1",
        sizeBytes: Long? = 10,
    ) = DiscoveredAudioFile(
        sourceId = "source-1",
        uri = uri,
        documentId = "document-1",
        displayName = "Track.mp3",
        format = DiscoveredAudioFormat.MP3,
        mimeType = "audio/mpeg",
        sizeBytes = sizeBytes,
        modifiedAtEpochMs = 900,
    )

    private fun hash(digest: String) = FullFileHashResult.Success("SHA-256", 1, digest, 10)

    private fun knownLocation(
        locator: String,
        digest: String,
        trackId: String = "track-1",
        locationId: String = "location-1",
    ) = KnownTrackLocation(
        trackId = TrackId(trackId),
        locationId = TrackLocationId(locationId),
        locator = FileLocator(locator),
        originalFileHash = fileHash(digest),
        currentFileHash = fileHash(digest),
    )

    private fun fileHash(digest: String) = FileHash(HashAlgorithmVersion("SHA-256", 1), digest)

    private object FixedIdFactory : CatalogIdFactory {
        override fun newTrackId() = TrackId("track-created")
        override fun newLocationId() = TrackLocationId("location-created")
    }

    private class FakeCatalogRepository(
        private val knownLocations: List<KnownTrackLocation>,
    ) : CatalogRepository {
        var listKnownTrackLocationsCalls = 0

        override fun listKnownTrackLocations(): List<KnownTrackLocation> {
            listKnownTrackLocationsCalls++
            return knownLocations
        }
        override fun insertTrack(track: TrackEntity) = Unit
        override fun findTrack(trackId: String): TrackEntity? = null
        override fun insertLocation(location: TrackLocationEntity) = Unit
        override fun findLocation(locationId: TrackLocationId): TrackLocationEntity? = null
        override fun bindLocator(binding: LocatorBindingEntity) = Unit
        override fun findBindingByLocator(locator: String): LocatorBindingEntity? =
            knownLocations.firstOrNull { it.locator.value == locator }?.let { known ->
                LocatorBindingEntity(
                    locator = known.locator.value,
                    locationId = known.locationId.value,
                    sourceId = "source-1",
                    boundAtEpochMs = 1_000,
                )
            }
        override fun findTrackWithLocations(trackId: String) = null
        override fun updateCurrentFileHash(locationId: TrackLocationId, expected: FileHash, replacement: FileHash) = false
        override fun updateAvailability(locationId: TrackLocationId, availability: LocationAvailability) = false
    }

    private class RecordingWriter : CatalogIdentityWriter {
        var writes = 0
        var createdTrack: TrackEntity? = null
        var createdLocation: TrackLocationEntity? = null
        var createdBinding: LocatorBindingEntity? = null

        override fun createTrackWithLocation(
            track: TrackEntity,
            location: TrackLocationEntity,
            binding: LocatorBindingEntity,
        ) {
            writes += 1
            createdTrack = track
            createdLocation = location
            createdBinding = binding
        }

        override fun attachLocation(location: TrackLocationEntity, binding: LocatorBindingEntity) {
            writes += 1
            createdLocation = location
            createdBinding = binding
        }
    }
}
