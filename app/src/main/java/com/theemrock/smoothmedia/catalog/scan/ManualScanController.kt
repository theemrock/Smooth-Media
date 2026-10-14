package com.theemrock.smoothmedia.catalog.scan

import android.content.ContentResolver
import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
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
import com.theemrock.smoothmedia.source.AndroidTreePermissionGateway
import java.util.UUID

/** Wires an explicit user-triggered scan to the existing discovery/hash/ingestion pipeline. */
class ManualScanController(
    private val database: CatalogDatabase,
    contentResolver: ContentResolver,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val newSessionId: () -> String = { UUID.randomUUID().toString() },
) {
    private val state = RoomScanStateRepository(database)
    private val discovery = SelectedAudioDiscovery(
        permissions = AndroidTreePermissionGateway(contentResolver),
        treeReader = AndroidSafDocumentTreeReader(contentResolver),
    )
    private val hasher = SafFullFileHasher(contentResolver)
    private val ingestion = CatalogIngestionUseCase(
        catalog = RoomCatalogRepository(database.trackDao(), database.locatorBindingDao()),
        writer = CatalogIdentityTransaction(database),
        hashIdentityAdapter = HashIdentityAdapter(),
        idFactory = UuidCatalogIdFactory,
        nowEpochMs = nowEpochMs,
        resolver = TrackIdentityResolver(LogicalMatchPolicy { _, _ -> LogicalMatchAssessment.NoMatch }),
    )

    fun start(source: LibrarySourceEntity, onProgress: (ManualScanProgress) -> Unit = {}): ManualScanResult {
        val session = ScanSessionEntity(
            sessionId = newSessionId(),
            status = ScanSessionStatus.RUNNING,
            startedAtEpochMs = nowEpochMs(),
            updatedAtEpochMs = nowEpochMs(),
            completedAtEpochMs = null,
        )
        return execute(
            session = session,
            source = source,
            resume = false,
            onProgress = onProgress,
        )
    }

    fun resume(sessionId: String, source: LibrarySourceEntity, onProgress: (ManualScanProgress) -> Unit = {}): ManualScanResult {
        val session = state.findSession(sessionId)
            ?: return ManualScanResult.Rejected(sessionId, ScanSessionOrchestrator.RejectionReason.RECOVERY_REJECTED)
        return execute(session, source, resume = true, onProgress = onProgress)
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
    ): ManualScanResult {
        val orchestrator = ScanSessionOrchestrator(
            state = state,
            discover = discovery::discover,
            beforeCandidate = { sessionId, _ ->
                val checkpoint = state.findCheckpoint(sessionId, source.sourceId)
                onProgress(
                    ManualScanProgress(
                        sessionId = sessionId,
                        status = state.findSession(sessionId)?.status ?: ScanSessionStatus.RUNNING,
                        processedCount = checkpoint?.processedCount ?: 0,
                        discoveredCount = checkpoint?.discoveredCount ?: 0,
                        errorCount = 0,
                    ),
                )
            },
            processor = ScanToCatalogPipeline(
                state = state,
                hash = hasher::hash,
                ingest = ingestion::ingest,
            ),
            nowEpochMs = nowEpochMs,
        )
        val result = if (resume) {
            orchestrator.resume(session.sessionId, source, nowEpochMs())
        } else {
            orchestrator.start(session, source)
        }
        val storedSession = state.findSession(session.sessionId)
        val checkpoint = state.findCheckpoint(session.sessionId, source.sourceId)
        onProgress(
            ManualScanProgress(
                sessionId = session.sessionId,
                status = storedSession?.status ?: statusFor(result),
                processedCount = checkpoint?.processedCount ?: 0,
                discoveredCount = checkpoint?.discoveredCount ?: 0,
                errorCount = when (result) {
                    is ScanSessionOrchestrator.Result.CandidateFailed -> 1
                    is ScanSessionOrchestrator.Result.Completed -> result.skippedUnreadableCount
                    else -> 0
                },
            ),
        )
        return result.toManualResult(
            defaultSessionId = session.sessionId,
            processedCount = checkpoint?.processedCount ?: 0,
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
    ): ManualScanResult = when (this) {
        is ScanSessionOrchestrator.Result.Completed -> ManualScanResult.Completed(
            sessionId = sessionId,
            skippedUnreadableCount = skippedUnreadableCount,
            processedCount = processedCount,
            successfulCount = (processedCount - skippedUnreadableCount).coerceAtLeast(0),
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
}

data class ManualScanProgress(
    val sessionId: String,
    val status: ScanSessionStatus,
    val processedCount: Int,
    val discoveredCount: Int,
    val errorCount: Int,
)

sealed interface ManualScanResult {
    data class Completed(
        val sessionId: String,
        val skippedUnreadableCount: Int,
        val processedCount: Int = 0,
        val successfulCount: Int = 0,
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
