package com.theemrock.smoothmedia.catalog.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateState
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.ingestion.CatalogIdFactory
import com.theemrock.smoothmedia.catalog.ingestion.CatalogIngestionResult
import com.theemrock.smoothmedia.catalog.ingestion.CatalogIngestionUseCase
import com.theemrock.smoothmedia.catalog.identity.LogicalMatchAssessment
import com.theemrock.smoothmedia.catalog.identity.LogicalMatchPolicy
import com.theemrock.smoothmedia.catalog.identity.TrackIdentityResolver
import com.theemrock.smoothmedia.catalog.model.TrackLocationId
import com.theemrock.smoothmedia.catalog.model.TrackId
import com.theemrock.smoothmedia.catalog.repository.CatalogIdentityTransaction
import com.theemrock.smoothmedia.catalog.repository.RoomCatalogRepository
import com.theemrock.smoothmedia.catalog.repository.RoomScanStateRepository
import com.theemrock.smoothmedia.catalog.scan.CandidateProcessingResult
import com.theemrock.smoothmedia.catalog.scan.ScanSessionOrchestrator
import com.theemrock.smoothmedia.catalog.scan.ScanToCatalogPipeline
import com.theemrock.smoothmedia.discovery.AudioDiscoveryResult
import com.theemrock.smoothmedia.discovery.AudioFormat
import com.theemrock.smoothmedia.discovery.DiscoveredAudioFile
import com.theemrock.smoothmedia.discovery.FullFileHashResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanCatalogPipelineRoomTest {
    private lateinit var database: CatalogDatabase
    private lateinit var state: RoomScanStateRepository
    private lateinit var ingestion: CatalogIngestionUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java)
            .addCallback(CatalogDatabaseCallback)
            .build()
        database.librarySourceDao().insert(source())
        state = RoomScanStateRepository(database)
        ingestion = CatalogIngestionUseCase(
            catalog = RoomCatalogRepository(database.trackDao(), database.locatorBindingDao()),
            writer = CatalogIdentityTransaction(database),
            hashIdentityAdapter = com.theemrock.smoothmedia.discovery.HashIdentityAdapter(),
            idFactory = FixedIdFactory,
            nowEpochMs = { 1_000L },
            resolver = TrackIdentityResolver(LogicalMatchPolicy { _, _ -> LogicalMatchAssessment.NoMatch }),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun successfulHashAndIngestion_completesCandidateAtomically() {
        val file = file("one")
        val pipeline = pipeline { _, _ -> FullFileHashResult.Success("SHA-256", 1, "digest-one", 10) }
        val orchestrator = orchestrator(file, pipeline)

        val result = orchestrator.start(session("scan-1"), source())

        assertTrue(result is ScanSessionOrchestrator.Result.Completed)
        assertEquals(ScanCandidateState.COMPLETED, state.findCandidate("scan-1", "source-1", "one")!!.state)
        assertEquals(1, database.trackDao().listLocations().size)
    }

    @Test
    fun hashFailure_leavesCandidateRetryableAndDoesNotWriteCatalog() {
        val file = file("one")
        val pipeline = pipeline { _, _ -> FullFileHashResult.ReadFailed }
        val orchestrator = orchestrator(file, pipeline)

        val result = orchestrator.start(session("scan-1"), source())

        assertTrue(result is ScanSessionOrchestrator.Result.CandidateFailed)
        assertEquals(ScanCandidateState.DISCOVERED, state.findCandidate("scan-1", "source-1", "one")!!.state)
        assertEquals(ScanSessionStatus.FAILED, state.findSession("scan-1")!!.status)
        assertTrue(database.trackDao().listLocations().isEmpty())
    }

    @Test
    fun repeatedRun_reusesExistingCatalogLocationWithoutSecondTrack() {
        val file = file("one")
        val first = orchestrator(file, pipeline { _, _ -> FullFileHashResult.Success("SHA-256", 1, "digest-one", 10) })
        assertTrue(first.start(session("scan-1"), source()) is ScanSessionOrchestrator.Result.Completed)

        val firstLocations = database.trackDao().listLocations()
        val firstBindings = database.locatorBindingDao().listAll()
        val firstTrackIds = firstLocations.map { it.trackId }.toSet()
        val firstLocationIds = firstLocations.map { it.locationId }.toSet()
        assertEquals(1, firstLocations.size)
        assertEquals(1, firstBindings.size)

        val second = orchestrator(file, pipeline { _, _ -> FullFileHashResult.Success("SHA-256", 1, "digest-one", 10) })
        assertTrue(second.start(session("scan-2"), source()) is ScanSessionOrchestrator.Result.Completed)

        val secondLocations = database.trackDao().listLocations()
        val secondBindings = database.locatorBindingDao().listAll()
        assertEquals(firstTrackIds, secondLocations.map { it.trackId }.toSet())
        assertEquals(firstLocationIds, secondLocations.map { it.locationId }.toSet())
        assertEquals(firstBindings, secondBindings)
        assertEquals(1, database.trackDao().listLocations().size)
        assertEquals(1, database.trackDao().listLocations().map { it.trackId }.toSet().size)
    }

    @Test
    fun recoveryAfterCatalogWrite_reusesExistingLocationWithoutSecondTrack() {
        val file = file("one")
        val hash = { _: DiscoveredAudioFile, _: com.theemrock.smoothmedia.discovery.HashCancellation ->
            FullFileHashResult.Success("SHA-256", 1, "digest-one", 10)
        }
        assertTrue(orchestrator(file, pipeline(hash)).start(session("scan-1"), source()) is ScanSessionOrchestrator.Result.Completed)

        state.startSession(session("scan-2"), listOf(com.theemrock.smoothmedia.catalog.database.entity.SourceScanCheckpointEntity(
            sessionId = "scan-2",
            sourceId = "source-1",
            continuationToken = null,
            discoveredCount = 0,
            processedCount = 0,
            coverage = com.theemrock.smoothmedia.catalog.database.entity.ScanCheckpointCoverage.PARTIAL,
            updatedAtEpochMs = 1_000L,
        )))
        state.recordCandidate(com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateEntity(
            sessionId = "scan-2",
            sourceId = "source-1",
            candidateKey = "one",
            state = ScanCandidateState.PROCESSING,
            locationId = null,
            updatedAtEpochMs = 1_500L,
        ))
        assertTrue(state.pauseSession("scan-2", 2_000L) is com.theemrock.smoothmedia.catalog.repository.ScanLifecycleResult.Applied)

        val resumed = orchestrator(file, pipeline(hash)).resume("scan-2", source(), 3_000L)

        assertTrue(resumed is ScanSessionOrchestrator.Result.Completed)
        assertEquals(1, database.trackDao().listLocations().size)
    }

    @Test
    fun reviewDecision_doesNotCompleteCandidateOrMergeIdentity() {
        val file = file("one")
        val pipeline = ScanToCatalogPipeline(
            state = state,
            hash = { _, _ -> FullFileHashResult.Success("SHA-256", 1, "digest-one", 10) },
            ingest = { _, _ -> CatalogIngestionResult.WriteBlocked("review-required") },
        )
        val result = orchestrator(file, pipeline).start(session("scan-1"), source())

        assertTrue(result is ScanSessionOrchestrator.Result.CandidateFailed)
        assertEquals(ScanCandidateState.DISCOVERED, state.findCandidate("scan-1", "source-1", "one")!!.state)
        assertTrue(database.trackDao().listLocations().isEmpty())
    }

    @Test
    fun pauseDuringHash_leavesCandidateRetryableWithoutCatalogWrite() {
        val file = file("one")
        val pipeline = pipeline { _, _ ->
            state.pauseSession("scan-1", 2_000L)
            FullFileHashResult.Cancelled
        }

        val result = orchestrator(file, pipeline).start(session("scan-1"), source())

        assertTrue(result is ScanSessionOrchestrator.Result.Paused)
        assertEquals(ScanCandidateState.DISCOVERED, state.findCandidate("scan-1", "source-1", "one")!!.state)
        assertEquals(ScanSessionStatus.PAUSED, state.findSession("scan-1")!!.status)
        assertTrue(database.trackDao().listLocations().isEmpty())
    }

    private fun orchestrator(file: DiscoveredAudioFile, pipeline: ScanToCatalogPipeline) =
        ScanSessionOrchestrator(
            state = state,
            discover = { AudioDiscoveryResult.Completed("source-1", listOf(file), 0) },
            processor = pipeline,
            nowEpochMs = { 1_000L },
        )

    private fun pipeline(
        hash: (DiscoveredAudioFile, com.theemrock.smoothmedia.discovery.HashCancellation) -> FullFileHashResult,
    ) = ScanToCatalogPipeline(
        state = state,
        hash = hash,
        ingest = ingestion::ingest,
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

    private fun file(id: String) = DiscoveredAudioFile(
        sourceId = "source-1",
        uri = "content://tree/music/$id",
        documentId = id,
        displayName = "$id.mp3",
        format = AudioFormat.MP3,
        mimeType = "audio/mpeg",
        sizeBytes = 10,
        modifiedAtEpochMs = 900L,
    )

    private fun session(id: String) = ScanSessionEntity(
        sessionId = id,
        status = ScanSessionStatus.RUNNING,
        startedAtEpochMs = 1_000L,
        updatedAtEpochMs = 1_000L,
        completedAtEpochMs = null,
    )

    private object FixedIdFactory : CatalogIdFactory {
        private var next = 1
        override fun newTrackId() = TrackId("pipeline-track-${next++}")
        override fun newLocationId() = TrackLocationId("pipeline-location-${next++}")
    }
}
