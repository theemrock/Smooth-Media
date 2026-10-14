package com.theemrock.smoothmedia.catalog.scan

import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.catalog.ingestion.CatalogIngestionResult
import com.theemrock.smoothmedia.catalog.ingestion.CatalogIngestionUseCase
import com.theemrock.smoothmedia.catalog.repository.ScanStateRepository
import com.theemrock.smoothmedia.discovery.DiscoveredAudioFile
import com.theemrock.smoothmedia.discovery.FullFileHashResult
import com.theemrock.smoothmedia.discovery.HashCancellation

/** Processes one discovered candidate without changing identity or review policy. */
class ScanToCatalogPipeline(
    private val state: ScanStateRepository,
    private val hash: (DiscoveredAudioFile, HashCancellation) -> FullFileHashResult,
    private val ingest: (DiscoveredAudioFile, FullFileHashResult) -> CatalogIngestionResult,
) : ScanCandidateProcessor {
    override fun process(
        sessionId: String,
        file: DiscoveredAudioFile,
    ): CandidateProcessingResult {
        fun status() = state.findSession(sessionId)?.status
        fun cancellationRequested() = status() != ScanSessionStatus.RUNNING

        val hashResult = hash(file, HashCancellation { cancellationRequested() })
        when (status()) {
            ScanSessionStatus.PAUSED -> return CandidateProcessingResult.Paused
            ScanSessionStatus.CANCELLED -> return CandidateProcessingResult.Cancelled
            ScanSessionStatus.RUNNING -> Unit
            else -> return CandidateProcessingResult.Failed(FailureReason.SESSION_NOT_RUNNING)
        }
        if (hashResult !is FullFileHashResult.Success) {
            return CandidateProcessingResult.Failed(FailureReason.HASH_FAILED)
        }

        val ingestion = ingest(file, hashResult)
        return when (ingestion) {
            is CatalogIngestionResult.Written ->
                CandidateProcessingResult.Completed(ingestion.locationId.value)
            is CatalogIngestionResult.Reused ->
                CandidateProcessingResult.Completed(ingestion.decision.locationId.value)
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
}

fun interface ScanCandidateProcessor {
    fun process(sessionId: String, file: DiscoveredAudioFile): CandidateProcessingResult
}

sealed interface CandidateProcessingResult {
    data class Completed(val locationId: String) : CandidateProcessingResult
    data class Failed(val reason: FailureReason) : CandidateProcessingResult
    data object Paused : CandidateProcessingResult
    data object Cancelled : CandidateProcessingResult
}

enum class FailureReason {
    HASH_FAILED,
    INGESTION_REJECTED,
    REVIEW_REQUIRED,
    WRITE_BLOCKED,
    STORAGE_FAILED,
    SESSION_NOT_RUNNING,
}
