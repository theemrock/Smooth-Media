package com.theemrock.smoothmedia.catalog.scan

import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanCheckpointCoverage
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.catalog.database.entity.SourceScanCheckpointEntity
import com.theemrock.smoothmedia.catalog.identity.IdentityDecision
import com.theemrock.smoothmedia.catalog.identity.NewTrackReason
import com.theemrock.smoothmedia.catalog.ingestion.CatalogIngestionResult
import com.theemrock.smoothmedia.catalog.model.TrackId
import com.theemrock.smoothmedia.catalog.model.TrackLocationId
import com.theemrock.smoothmedia.catalog.repository.LeaseRecoveryResult
import com.theemrock.smoothmedia.catalog.repository.ScanLifecycleResult
import com.theemrock.smoothmedia.catalog.repository.ScanRecoveryResult
import com.theemrock.smoothmedia.catalog.repository.ScanStateRepository
import com.theemrock.smoothmedia.discovery.AudioFormat
import com.theemrock.smoothmedia.discovery.DiscoveredAudioFile
import com.theemrock.smoothmedia.discovery.FullFileHashResult
import com.theemrock.smoothmedia.discovery.HashCancellation
import com.theemrock.smoothmedia.metadata.MetadataEnrichmentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanToCatalogPipelineTest {
    @Test
    fun processedCandidate_recordsAnonymousHashIngestionAndMetadataMetrics() {
        val profiler = ScanPerformanceProfiler()
        val pipeline = ScanToCatalogPipeline(
            state = RunningScanState(),
            hash = { _, _ -> FullFileHashResult.Success("SHA-256", 1, "digest", 10) },
            ingest = { _, _ -> written("track", "location") },
            enrichMetadata = { MetadataEnrichmentResult.Updated(it, "location") },
            profiler = profiler,
        )

        assertTrue(pipeline.process("session", file()) is CandidateProcessingResult.Completed)

        assertEquals(1, profiler.snapshot().single { it.stage == ScanPerformanceStage.FULL_FILE_HASH_INCLUSIVE }.count)
        assertEquals(1, profiler.snapshot().single { it.stage == ScanPerformanceStage.IDENTITY_CATALOG_INGESTION_INCLUSIVE }.count)
        assertEquals(1, profiler.snapshot().single { it.stage == ScanPerformanceStage.METADATA_EXTRACTION_INCLUSIVE }.count)
    }

    @Test
    fun writtenCandidate_enrichesItsTrack_andKeepsCatalogSuccessWhenMetadataFails() {
        val state = RunningScanState()
        val metadataCalls = mutableListOf<String>()
        val pipeline = ScanToCatalogPipeline(
            state = state,
            hash = { _, _ -> FullFileHashResult.Success("SHA-256", 1, "digest", 10) },
            ingest = { _, _ -> written("track-new", "location-new") },
            enrichMetadata = { trackId ->
                metadataCalls += trackId
                MetadataEnrichmentResult.ReadFailed(trackId, emptyList())
            },
        )

        val result = pipeline.process("session", file())

        assertTrue(result is CandidateProcessingResult.Completed)
        assertEquals(listOf("track-new"), metadataCalls)
        assertEquals(
            ScanMetadataResult.FAILED,
            (result as CandidateProcessingResult.Completed).metadataResult,
        )
    }

    @Test
    fun reusedCandidate_enrichesExistingTrack() {
        val state = RunningScanState()
        val metadataCalls = mutableListOf<String>()
        val pipeline = ScanToCatalogPipeline(
            state = state,
            hash = { _, _ -> FullFileHashResult.Success("SHA-256", 1, "digest", 10) },
            ingest = { _, _ -> CatalogIngestionResult.Reused(
                IdentityDecision.ReuseLocation(
                    trackId = TrackId("track-existing"),
                    locationId = TrackLocationId("location-existing"),
                    matchedHash = com.theemrock.smoothmedia.catalog.identity.HashMatch.CURRENT,
                ),
            ) },
            enrichMetadata = { trackId ->
                metadataCalls += trackId
                MetadataEnrichmentResult.Updated(trackId, "location-existing")
            },
        )

        val result = pipeline.process("session", file())

        assertTrue(result is CandidateProcessingResult.Completed)
        assertEquals(listOf("track-existing"), metadataCalls)
        assertEquals(ScanMetadataResult.ENRICHED, (result as CandidateProcessingResult.Completed).metadataResult)
    }

    @Test
    fun cancelledCandidate_doesNotStartMetadataEnrichment() {
        val state = RunningScanState(status = ScanSessionStatus.CANCELLED)
        var metadataCalls = 0
        val pipeline = ScanToCatalogPipeline(
            state = state,
            hash = { _, _ -> FullFileHashResult.Success("SHA-256", 1, "digest", 10) },
            ingest = { _, _ -> written("track", "location") },
            enrichMetadata = {
                metadataCalls++
                MetadataEnrichmentResult.Updated("track", "location")
            },
        )

        val result = pipeline.process("session", file())

        assertEquals(CandidateProcessingResult.Cancelled, result)
        assertEquals(0, metadataCalls)
    }

    @Test
    fun completedCandidate_enrichesExistingLocation_withoutRehashing() {
        val state = RunningScanState()
        var hashCalls = 0
        val pipeline = ScanToCatalogPipeline(
            state = state,
            hash = { _, _ ->
                hashCalls++
                FullFileHashResult.Success("SHA-256", 1, "digest", 10)
            },
            ingest = { _, _ -> error("completed candidates must not ingest") },
            enrichExistingLocation = { locationId ->
                MetadataEnrichmentResult.Updated("track-existing", locationId)
            },
        )

        val result = pipeline.processCompletedCandidate(
            sessionId = "session",
            file = file(),
            candidate = ScanCandidateEntity(
                sessionId = "session",
                sourceId = "source",
                candidateKey = "song",
                state = com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateState.COMPLETED,
                locationId = "location-existing",
                updatedAtEpochMs = 1_000L,
            ),
        )

        assertEquals(0, hashCalls)
        assertEquals(ScanMetadataResult.ENRICHED, (result as CandidateProcessingResult.Completed).metadataResult)
    }

    private fun written(trackId: String, locationId: String) = CatalogIngestionResult.Written(
        decision = IdentityDecision.CreateNewTrack(NewTrackReason.NO_LOGICAL_MATCH),
        trackId = TrackId(trackId),
        locationId = TrackLocationId(locationId),
    )

    private fun file() = DiscoveredAudioFile(
        sourceId = "source",
        uri = "content://source/song.mp3",
        documentId = "song",
        displayName = "song.mp3",
        format = AudioFormat.MP3,
        mimeType = "audio/mpeg",
        sizeBytes = 10,
        modifiedAtEpochMs = 1_000L,
    )

    private class RunningScanState(
        private var status: ScanSessionStatus = ScanSessionStatus.RUNNING,
    ) : ScanStateRepository {
        override fun findSession(sessionId: String) = ScanSessionEntity(
            sessionId = sessionId,
            status = status,
            startedAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            completedAtEpochMs = null,
        )

        override fun startSession(session: ScanSessionEntity, checkpoints: List<SourceScanCheckpointEntity>) = Unit
        override fun recoverStaleLeaseAndStart(session: ScanSessionEntity, checkpoints: List<SourceScanCheckpointEntity>) =
            LeaseRecoveryResult.Started(session.sessionId, false)
        override fun updateCheckpoint(sessionId: String, sourceId: String, continuationToken: String?, discoveredCount: Int, processedCount: Int, coverage: ScanCheckpointCoverage, updatedAtEpochMs: Long) = true
        override fun findCheckpoint(sessionId: String, sourceId: String): SourceScanCheckpointEntity? = null

        override fun heartbeat(sessionId: String, updatedAtEpochMs: Long) = true
        override fun recordCandidate(candidate: ScanCandidateEntity) = Unit
        override fun findCandidate(sessionId: String, sourceId: String, candidateKey: String): ScanCandidateEntity? = null
        override fun updateCandidateState(sessionId: String, sourceId: String, candidateKey: String, state: com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateState, locationId: String?, updatedAtEpochMs: Long) = true
        override fun recoverInterruptedSession(sessionId: String, updatedAtEpochMs: Long, staleAfterMs: Long) = ScanRecoveryResult.Recovered(sessionId, 0)
        override fun completeSession(sessionId: String, completedAtEpochMs: Long) = Unit
        override fun pauseSession(sessionId: String, updatedAtEpochMs: Long) = ScanLifecycleResult.Applied(sessionId, ScanSessionStatus.PAUSED)
        override fun resumeSession(sessionId: String, updatedAtEpochMs: Long) = ScanLifecycleResult.Applied(sessionId, ScanSessionStatus.RUNNING)
        override fun cancelSession(sessionId: String, updatedAtEpochMs: Long) = ScanLifecycleResult.Applied(sessionId, ScanSessionStatus.CANCELLED)
        override fun failSession(sessionId: String, updatedAtEpochMs: Long) = ScanLifecycleResult.Applied(sessionId, ScanSessionStatus.FAILED)
        override fun isMissingReconciliationAllowed(sessionId: String, sourceId: String) = false
    }
}
