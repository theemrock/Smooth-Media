package com.theemrock.smoothmedia.catalog.scan

import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.catalog.ingestion.CatalogIngestionResult
import com.theemrock.smoothmedia.catalog.ingestion.CatalogIngestionUseCase
import com.theemrock.smoothmedia.catalog.repository.ScanStateRepository
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateEntity
import com.theemrock.smoothmedia.discovery.DiscoveredAudioFile
import com.theemrock.smoothmedia.discovery.FullFileHashResult
import com.theemrock.smoothmedia.discovery.HashCancellation
import com.theemrock.smoothmedia.metadata.MetadataEnrichmentResult

/** Processes one discovered candidate without changing identity or review policy. */
class ScanToCatalogPipeline(
    private val state: ScanStateRepository,
    private val hash: (DiscoveredAudioFile, HashCancellation) -> FullFileHashResult,
    private val ingest: (DiscoveredAudioFile, FullFileHashResult) -> CatalogIngestionResult,
    private val enrichMetadata: ((String) -> MetadataEnrichmentResult)? = null,
    private val enrichExistingLocation: ((String) -> MetadataEnrichmentResult)? = null,
    private val profiler: ScanPerformanceProfiler? = null,
) : OrderedHashScanCandidateProcessor {
    override fun process(
        sessionId: String,
        file: DiscoveredAudioFile,
    ): CandidateProcessingResult {
        val cancellation = PeriodicScanCancellation(
            statusProvider = { state.findSession(sessionId)?.status },
        )
        return processHashed(sessionId, file, hashFile(sessionId, file, cancellation))
    }

    override fun hashFile(
        sessionId: String,
        file: DiscoveredAudioFile,
        cancellation: HashCancellation,
    ): FullFileHashResult {
        return profiler?.measure(ScanPerformanceStage.FULL_FILE_HASH_INCLUSIVE) {
            hash(file, cancellation)
        } ?: hash(file, cancellation)
    }

    override fun processHashed(
        sessionId: String,
        file: DiscoveredAudioFile,
        hashResult: FullFileHashResult,
    ): CandidateProcessingResult {
        when (status(sessionId)) {
            ScanSessionStatus.PAUSED -> return CandidateProcessingResult.Paused
            ScanSessionStatus.CANCELLED -> return CandidateProcessingResult.Cancelled
            ScanSessionStatus.RUNNING -> Unit
            else -> return CandidateProcessingResult.Failed(FailureReason.SESSION_NOT_RUNNING)
        }
        if (hashResult !is FullFileHashResult.Success) {
            return CandidateProcessingResult.Failed(FailureReason.HASH_FAILED)
        }

        val ingestion = profiler?.measure(ScanPerformanceStage.IDENTITY_CATALOG_INGESTION_INCLUSIVE) {
            ingest(file, hashResult)
        } ?: ingest(file, hashResult)
        return when (ingestion) {
            is CatalogIngestionResult.Written -> completeWithMetadata(
                sessionId = sessionId,
                trackId = ingestion.trackId.value,
                locationId = ingestion.locationId.value,
            )
            is CatalogIngestionResult.Reused -> completeWithMetadata(
                sessionId = sessionId,
                trackId = ingestion.decision.trackId.value,
                locationId = ingestion.decision.locationId.value,
            )
            is CatalogIngestionResult.NeedsReview ->
                CandidateProcessingResult.Failed(FailureReason.REVIEW_REQUIRED)
            is CatalogIngestionResult.WriteBlocked ->
                CandidateProcessingResult.Failed(FailureReason.WRITE_BLOCKED)
            is CatalogIngestionResult.StorageFailed ->
                CandidateProcessingResult.Failed(FailureReason.STORAGE_FAILED)
            is CatalogIngestionResult.HashUnavailable,
            is CatalogIngestionResult.UnsupportedHash,
            is CatalogIngestionResult.InvalidInput ->
                CandidateProcessingResult.Failed(FailureReason.INGESTION_REJECTED)
        }
    }

    private fun status(sessionId: String) = state.findSession(sessionId)?.status

    override fun reprocessCompletedCandidates(): Boolean = enrichExistingLocation != null

    override fun processCompletedCandidate(
        sessionId: String,
        file: DiscoveredAudioFile,
        candidate: ScanCandidateEntity,
    ): CandidateProcessingResult {
        when (state.findSession(sessionId)?.status) {
            ScanSessionStatus.PAUSED -> return CandidateProcessingResult.Paused
            ScanSessionStatus.CANCELLED -> return CandidateProcessingResult.Cancelled
            ScanSessionStatus.RUNNING -> Unit
            else -> return CandidateProcessingResult.Failed(FailureReason.SESSION_NOT_RUNNING)
        }
        val locationId = candidate.locationId
            ?: return CandidateProcessingResult.Failed(FailureReason.METADATA_LOCATION_MISSING)
        val metadataResult = enrichExistingLocation?.let { enrich ->
            runCatching {
                profiler?.measure(ScanPerformanceStage.METADATA_EXTRACTION_INCLUSIVE) {
                    enrich(locationId)
                } ?: enrich(locationId)
            }.fold(
                onSuccess = { it.toScanMetadataResult() },
                onFailure = { ScanMetadataResult.FAILED },
            )
        } ?: run {
            profiler?.markSkipped(ScanPerformanceStage.METADATA_EXTRACTION_INCLUSIVE)
            ScanMetadataResult.SKIPPED
        }
        return CandidateProcessingResult.Completed(locationId, metadataResult)
    }

    private fun completeWithMetadata(
        sessionId: String,
        trackId: String,
        locationId: String,
    ): CandidateProcessingResult {
        when (state.findSession(sessionId)?.status) {
            ScanSessionStatus.PAUSED -> return CandidateProcessingResult.Paused
            ScanSessionStatus.CANCELLED -> return CandidateProcessingResult.Cancelled
            ScanSessionStatus.RUNNING -> Unit
            else -> return CandidateProcessingResult.Failed(FailureReason.SESSION_NOT_RUNNING)
        }
        val metadataResult = enrichMetadata?.let { enrich ->
            runCatching {
                profiler?.measure(ScanPerformanceStage.METADATA_EXTRACTION_INCLUSIVE) {
                    enrich(trackId)
                } ?: enrich(trackId)
            }.fold(
                onSuccess = { it.toScanMetadataResult() },
                onFailure = { ScanMetadataResult.FAILED },
            )
        } ?: run {
            profiler?.markSkipped(ScanPerformanceStage.METADATA_EXTRACTION_INCLUSIVE)
            ScanMetadataResult.SKIPPED
        }
        return CandidateProcessingResult.Completed(locationId, metadataResult)
    }

    private fun MetadataEnrichmentResult.toScanMetadataResult() = when (this) {
        is MetadataEnrichmentResult.Updated -> ScanMetadataResult.ENRICHED
        is MetadataEnrichmentResult.ReadFailed,
        is MetadataEnrichmentResult.NoAccessibleLocation,
        is MetadataEnrichmentResult.Excluded,
        is MetadataEnrichmentResult.TrackNotFound,
        -> ScanMetadataResult.FAILED
    }
}

interface OrderedHashScanCandidateProcessor : ScanCandidateProcessor {
    fun hashFile(sessionId: String, file: DiscoveredAudioFile, cancellation: HashCancellation): FullFileHashResult

    fun processHashed(
        sessionId: String,
        file: DiscoveredAudioFile,
        hashResult: FullFileHashResult,
    ): CandidateProcessingResult
}

/**
 * Keeps the first cancellation check immediate while avoiding a Room lookup for every hash chunk.
 * A blocking stream read can exceed this interval; this is a refresh cadence, not a hard latency
 * guarantee. The mandatory post-hash session check remains in [ScanToCatalogPipeline.process].
 */
internal class PeriodicScanCancellation(
    private val statusProvider: () -> ScanSessionStatus?,
    private val nowNanos: () -> Long = System::nanoTime,
    private val refreshIntervalNanos: Long = DEFAULT_REFRESH_INTERVAL_NANOS,
) : HashCancellation {
    init {
        require(refreshIntervalNanos > 0) { "refreshIntervalNanos must be positive" }
    }

    private var lastCheckedAtNanos: Long? = null
    private var cancelled = false

    override fun isCancelled(): Boolean {
        if (cancelled) return true
        val now = nowNanos()
        val lastChecked = lastCheckedAtNanos
        if (lastChecked == null || now - lastChecked >= refreshIntervalNanos) {
            lastCheckedAtNanos = now
            cancelled = statusProvider() != ScanSessionStatus.RUNNING
        }
        return cancelled
    }

    private companion object {
        const val DEFAULT_REFRESH_INTERVAL_NANOS = 100_000_000L
    }
}

fun interface ScanCandidateProcessor {
    fun process(sessionId: String, file: DiscoveredAudioFile): CandidateProcessingResult

    fun reprocessCompletedCandidates(): Boolean = false

    fun processCompletedCandidate(
        sessionId: String,
        file: DiscoveredAudioFile,
        candidate: ScanCandidateEntity,
    ): CandidateProcessingResult = process(sessionId, file)
}

sealed interface CandidateProcessingResult {
    data class Completed(
        val locationId: String,
        val metadataResult: ScanMetadataResult = ScanMetadataResult.SKIPPED,
    ) : CandidateProcessingResult
    data class Failed(val reason: FailureReason) : CandidateProcessingResult
    data object Paused : CandidateProcessingResult
    data object Cancelled : CandidateProcessingResult
}

enum class ScanMetadataResult {
    ENRICHED,
    FAILED,
    SKIPPED,
}

enum class FailureReason {
    HASH_FAILED,
    INGESTION_REJECTED,
    REVIEW_REQUIRED,
    WRITE_BLOCKED,
    STORAGE_FAILED,
    SESSION_NOT_RUNNING,
    METADATA_LOCATION_MISSING,
}
