package com.theemrock.smoothmedia.catalog.scan

import android.os.SystemClock
import android.util.Log
import android.content.ContentResolver
import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.catalog.database.entity.SafDocumentEntity
import com.theemrock.smoothmedia.catalog.ingestion.CatalogIdFactory
import com.theemrock.smoothmedia.catalog.ingestion.CatalogIngestionUseCase
import com.theemrock.smoothmedia.catalog.identity.LogicalMatchAssessment
import com.theemrock.smoothmedia.catalog.identity.LogicalMatchPolicy
import com.theemrock.smoothmedia.catalog.identity.TrackIdentityResolver
import com.theemrock.smoothmedia.catalog.model.TrackId
import com.theemrock.smoothmedia.catalog.model.TrackLocationId
import com.theemrock.smoothmedia.catalog.repository.CatalogIdentityTransaction
import com.theemrock.smoothmedia.catalog.repository.RoomCatalogRepository
import com.theemrock.smoothmedia.catalog.repository.RoomScanStateRepository
import com.theemrock.smoothmedia.discovery.AndroidSafDocumentTreeReader
import com.theemrock.smoothmedia.discovery.HashIdentityAdapter
import com.theemrock.smoothmedia.discovery.SafFullFileHasher
import com.theemrock.smoothmedia.discovery.SelectedAudioDiscovery
import com.theemrock.smoothmedia.metadata.MetadataEnrichmentResult
import com.theemrock.smoothmedia.source.AndroidTreePermissionGateway
import java.util.UUID

/** Wires an explicit user-triggered scan to the existing discovery/hash/ingestion pipeline. */
class ManualScanController(
    private val database: CatalogDatabase,
    private val contentResolver: ContentResolver,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val newSessionId: () -> String = { UUID.randomUUID().toString() },
    private val enrichMetadata: ((String) -> MetadataEnrichmentResult)? = null,
) {
    private val state = RoomScanStateRepository(database)
    private val discovery = SelectedAudioDiscovery(
        permissions = AndroidTreePermissionGateway(contentResolver),
        treeReader = AndroidSafDocumentTreeReader(contentResolver),
        onDocument = { sourceId, document ->
            database.safDocumentDao().upsert(
                SafDocumentEntity(
                    sourceId = sourceId,
                    documentId = document.documentId,
                    uri = document.uri,
                    parentDocumentId = document.parentDocumentId,
                    displayName = document.displayName,
                    isDirectory = document.isDirectory,
                    available = true,
                    updatedAtEpochMs = nowEpochMs(),
                ),
            )
        },
    )
    fun start(
        source: LibrarySourceEntity,
        onProgress: (ManualScanProgress) -> Unit = {},
        onSessionStarted: (String) -> Unit = {},
    ): ManualScanResult {
        val scanStartElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        val session = ScanSessionEntity(
            sessionId = newSessionId(),
            status = ScanSessionStatus.RUNNING,
            startedAtEpochMs = nowEpochMs(),
            updatedAtEpochMs = nowEpochMs(),
            completedAtEpochMs = null,
        )
        Log.i(P10_TAG, "SCAN_START|elapsedRealtimeNanos=$scanStartElapsedRealtimeNanos")
        onSessionStarted(session.sessionId)
        val profiler = ScanPerformanceProfiler()
        val result = execute(
            session = session,
            source = source,
            resume = false,
            onProgress = onProgress,
            profiler = profiler,
            totalStartedAtElapsedRealtimeNanos = scanStartElapsedRealtimeNanos,
        )
        val checkpoint = state.findCheckpoint(session.sessionId, source.sourceId)
        val completedAtElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        Log.i(
            P10_TAG,
            "SCAN_COMPLETE|elapsedRealtimeNanos=$completedAtElapsedRealtimeNanos|elapsedNanos=${completedAtElapsedRealtimeNanos - scanStartElapsedRealtimeNanos}|discovered=${checkpoint?.discoveredCount ?: 0}|processed=${checkpoint?.processedCount ?: 0}|result=${result::class.simpleName}",
        )
        return result
    }

    fun resume(
        sessionId: String,
        source: LibrarySourceEntity,
        onProgress: (ManualScanProgress) -> Unit = {},
        onSessionStarted: (String) -> Unit = {},
    ): ManualScanResult {
        val session = state.findSession(sessionId)
            ?: return ManualScanResult.Rejected(sessionId, ScanSessionOrchestrator.RejectionReason.RECOVERY_REJECTED)
        onSessionStarted(session.sessionId)
        return execute(
            session,
            source,
            resume = true,
            onProgress = onProgress,
            profiler = ScanPerformanceProfiler(),
            totalStartedAtElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
        )
    }

    fun pause(sessionId: String): Boolean =
        state.pauseSession(sessionId, nowEpochMs()) is com.theemrock.smoothmedia.catalog.repository.ScanLifecycleResult.Applied

    fun cancel(sessionId: String): Boolean =
        state.cancelSession(sessionId, nowEpochMs()) is com.theemrock.smoothmedia.catalog.repository.ScanLifecycleResult.Applied

    private fun execute(
        session: ScanSessionEntity,
        source: LibrarySourceEntity,
        resume: Boolean,
        onProgress: (ManualScanProgress) -> Unit,
        profiler: ScanPerformanceProfiler,
        totalStartedAtElapsedRealtimeNanos: Long,
    ): ManualScanResult {
        var firstDiscoveryLogged = false
        var firstCatalogRecordLogged = false
        var enrichedCount = 0
        var metadataFailedCount = 0
        var phase = if (resume) ScanPhase.RESUMING else ScanPhase.STARTING
        var timing = ScanTiming()
        fun emitProgress() {
            val checkpoint = state.findCheckpoint(session.sessionId, source.sourceId)
            onProgress(
                ManualScanProgress(
                    sessionId = session.sessionId,
                    status = state.findSession(session.sessionId)?.status ?: ScanSessionStatus.RUNNING,
                    phase = phase,
                    processedCount = checkpoint?.processedCount ?: 0,
                    discoveredCount = checkpoint?.discoveredCount ?: 0,
                    errorCount = 0,
                    enrichedCount = enrichedCount,
                    metadataFailedCount = metadataFailedCount,
                    timing = timing,
                ),
            )
        }
        val hasher = SafFullFileHasher(
            contentResolver,
            onStreamOpenDurationNanos = { profiler.record(ScanPerformanceStage.FILE_OPEN, it) },
        )
        val ingestion = CatalogIngestionUseCase(
            catalog = RoomCatalogRepository(database.trackDao(), database.locatorBindingDao()),
            writer = CatalogIdentityTransaction(
                database,
                onTransactionDurationNanos = { profiler.record(ScanPerformanceStage.ROOM_TRANSACTION, it) },
            ),
            hashIdentityAdapter = HashIdentityAdapter(),
            idFactory = UuidCatalogIdFactory,
            nowEpochMs = nowEpochMs,
            resolver = TrackIdentityResolver(LogicalMatchPolicy { _, _ -> LogicalMatchAssessment.NoMatch }),
        )
        val orchestrator = ScanSessionOrchestrator(
            state = state,
            discover = discovery::discover,
            beforeCandidate = { sessionId, _ ->
                if (!firstDiscoveryLogged) {
                    firstDiscoveryLogged = true
                    Log.i(P10_TAG, "FIRST_DISCOVERY|elapsedRealtimeNanos=${SystemClock.elapsedRealtimeNanos()}")
                }
                val checkpoint = state.findCheckpoint(sessionId, source.sourceId)
                onProgress(
                    ManualScanProgress(
                        sessionId = sessionId,
                        status = state.findSession(sessionId)?.status ?: ScanSessionStatus.RUNNING,
                        phase = phase,
                        processedCount = checkpoint?.processedCount ?: 0,
                        discoveredCount = checkpoint?.discoveredCount ?: 0,
                        errorCount = 0,
                        enrichedCount = enrichedCount,
                        metadataFailedCount = metadataFailedCount,
                        timing = timing,
                    ),
                )
            },
            processor = ScanToCatalogPipeline(
                state = state,
                hash = hasher::hash,
                ingest = ingestion::ingest,
                enrichMetadata = enrichMetadata,
                enrichExistingLocation = enrichMetadata?.let { enrich ->
                    { locationId ->
                        val trackId = database.trackDao().findLocation(locationId)?.trackId
                        if (trackId == null) {
                            com.theemrock.smoothmedia.metadata.MetadataEnrichmentResult.TrackNotFound(locationId)
                        } else {
                            enrich(trackId)
                        }
                    }
                },
            ),
            afterCandidate = { sessionId, _, result ->
                if (!firstCatalogRecordLogged) {
                    firstCatalogRecordLogged = true
                    Log.i(P10_TAG, "FIRST_CATALOG_RECORD|elapsedRealtimeNanos=${SystemClock.elapsedRealtimeNanos()}")
                }
                when (result.metadataResult) {
                    ScanMetadataResult.ENRICHED -> enrichedCount++
                    ScanMetadataResult.FAILED -> metadataFailedCount++
                    ScanMetadataResult.SKIPPED -> Unit
                }
                val checkpoint = state.findCheckpoint(sessionId, source.sourceId)
                onProgress(
                    ManualScanProgress(
                        sessionId = sessionId,
                        status = state.findSession(sessionId)?.status ?: ScanSessionStatus.RUNNING,
                        phase = phase,
                        processedCount = (checkpoint?.processedCount ?: 0) + 1,
                        discoveredCount = checkpoint?.discoveredCount ?: 0,
                        errorCount = 0,
                        enrichedCount = enrichedCount,
                        metadataFailedCount = metadataFailedCount,
                        timing = timing,
                    ),
                )
            },
            onPhaseChanged = { next ->
                phase = next
                emitProgress()
            },
            onTiming = { next ->
                timing = next
            },
            nowEpochMs = nowEpochMs,
            profiler = profiler,
        )
        emitProgress()
        val result = try {
            if (resume) {
                orchestrator.resume(session.sessionId, source, nowEpochMs())
            } else {
                orchestrator.start(session, source)
            }
        } finally {
            profiler.record(
                ScanPerformanceStage.TOTAL_SCAN,
                SystemClock.elapsedRealtimeNanos() - totalStartedAtElapsedRealtimeNanos,
            )
            Log.i(PERF_TAG, profiler.summaryLogLine())
        }
        val storedSession = state.findSession(session.sessionId)
        val checkpoint = state.findCheckpoint(session.sessionId, source.sourceId)
        onProgress(
            ManualScanProgress(
                sessionId = session.sessionId,
                status = storedSession?.status ?: statusFor(result),
                phase = phase,
                processedCount = checkpoint?.processedCount ?: 0,
                discoveredCount = checkpoint?.discoveredCount ?: 0,
                errorCount = when (result) {
                    is ScanSessionOrchestrator.Result.CandidateFailed -> 1
                    is ScanSessionOrchestrator.Result.Completed -> result.skippedUnreadableCount
                    else -> 0
                },
                enrichedCount = enrichedCount,
                metadataFailedCount = metadataFailedCount,
                timing = timing,
            ),
        )
        return result.toManualResult(
            defaultSessionId = session.sessionId,
            processedCount = checkpoint?.processedCount ?: 0,
            enrichedCount = enrichedCount,
            metadataFailedCount = metadataFailedCount,
        )
    }

    private fun statusFor(result: ScanSessionOrchestrator.Result): ScanSessionStatus = when (result) {
        is ScanSessionOrchestrator.Result.Completed -> ScanSessionStatus.COMPLETED
        is ScanSessionOrchestrator.Result.Paused -> ScanSessionStatus.PAUSED
        is ScanSessionOrchestrator.Result.Cancelled -> ScanSessionStatus.CANCELLED
        is ScanSessionOrchestrator.Result.CandidateFailed,
        is ScanSessionOrchestrator.Result.DiscoveryFailed,
        -> ScanSessionStatus.FAILED
        is ScanSessionOrchestrator.Result.AlreadyActive,
        is ScanSessionOrchestrator.Result.Rejected,
        -> ScanSessionStatus.FAILED
    }

    private fun ScanSessionOrchestrator.Result.toManualResult(
        defaultSessionId: String,
        processedCount: Int,
        enrichedCount: Int,
        metadataFailedCount: Int,
    ): ManualScanResult = when (this) {
        is ScanSessionOrchestrator.Result.Completed -> ManualScanResult.Completed(
            sessionId = sessionId,
            skippedUnreadableCount = skippedUnreadableCount,
            processedCount = processedCount,
            successfulCount = (processedCount - skippedUnreadableCount).coerceAtLeast(0),
            enrichedCount = enrichedCount,
            metadataFailedCount = metadataFailedCount,
        )
        is ScanSessionOrchestrator.Result.Paused -> ManualScanResult.Paused(sessionId)
        is ScanSessionOrchestrator.Result.Cancelled -> ManualScanResult.Cancelled(sessionId)
        is ScanSessionOrchestrator.Result.AlreadyActive -> ManualScanResult.AlreadyActive(sessionId)
        is ScanSessionOrchestrator.Result.DiscoveryFailed -> ManualScanResult.DiscoveryFailed(defaultSessionId)
        is ScanSessionOrchestrator.Result.CandidateFailed -> ManualScanResult.CandidateFailed(sessionId, reason)
        is ScanSessionOrchestrator.Result.Rejected -> ManualScanResult.Rejected(defaultSessionId, reason)
    }

    private object UuidCatalogIdFactory : CatalogIdFactory {
        override fun newTrackId() = TrackId("track-${UUID.randomUUID()}")

        override fun newLocationId() = TrackLocationId("location-${UUID.randomUUID()}")
    }

    private companion object {
        const val P10_TAG = "P10Baseline"
        const val PERF_TAG = "SMOOTH_SCAN_PERF"
    }
}

data class ManualScanProgress(
    val sessionId: String,
    val status: ScanSessionStatus,
    val phase: ScanPhase = ScanPhase.PROCESSING,
    val processedCount: Int,
    val discoveredCount: Int,
    val errorCount: Int,
    val enrichedCount: Int = 0,
    val metadataFailedCount: Int = 0,
    val timing: ScanTiming = ScanTiming(),
)

sealed interface ManualScanResult {
    data class Completed(
        val sessionId: String,
        val skippedUnreadableCount: Int,
        val processedCount: Int = 0,
        val successfulCount: Int = 0,
        val enrichedCount: Int = 0,
        val metadataFailedCount: Int = 0,
    ) : ManualScanResult
    data class Paused(val sessionId: String) : ManualScanResult
    data class Cancelled(val sessionId: String) : ManualScanResult
    data class AlreadyActive(val sessionId: String) : ManualScanResult
    data class DiscoveryFailed(val sessionId: String) : ManualScanResult
    data class CandidateFailed(val sessionId: String, val reason: FailureReason) : ManualScanResult
    data class Rejected(val sessionId: String, val reason: ScanSessionOrchestrator.RejectionReason) : ManualScanResult
}

internal fun ManualScanResult.status(): ScanSessionStatus = when (this) {
    is ManualScanResult.Completed -> ScanSessionStatus.COMPLETED
    is ManualScanResult.Paused -> ScanSessionStatus.PAUSED
    is ManualScanResult.Cancelled -> ScanSessionStatus.CANCELLED
    is ManualScanResult.CandidateFailed,
    is ManualScanResult.DiscoveryFailed,
    is ManualScanResult.AlreadyActive,
    is ManualScanResult.Rejected,
    -> ScanSessionStatus.FAILED
}
