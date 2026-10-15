package com.theemrock.smoothmedia

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.scan.ManualScanController
import com.theemrock.smoothmedia.catalog.scan.ManualScanProgress
import com.theemrock.smoothmedia.catalog.scan.ManualScanResult
import com.theemrock.smoothmedia.catalog.scan.ScanSessionOrchestrator
import com.theemrock.smoothmedia.library.LibraryViewModel
import com.theemrock.smoothmedia.library.LibraryCollectionType
import com.theemrock.smoothmedia.library.LibraryCollectionViewModel
import com.theemrock.smoothmedia.library.LibraryCollectionRow
import com.theemrock.smoothmedia.library.LibraryTrackFilter
import com.theemrock.smoothmedia.library.RoomLibraryRepository
import com.theemrock.smoothmedia.ui.LibraryScreen
import com.theemrock.smoothmedia.ui.LibraryCollectionScreen
import com.theemrock.smoothmedia.metadata.AndroidAudioMetadataReader
import com.theemrock.smoothmedia.metadata.MetadataBatchProgress
import com.theemrock.smoothmedia.metadata.MetadataBatchCoordinator
import com.theemrock.smoothmedia.metadata.MetadataContinuousSessionCoordinator
import com.theemrock.smoothmedia.metadata.MetadataContinuousSessionResult
import com.theemrock.smoothmedia.metadata.MetadataSessionLaunchGate
import com.theemrock.smoothmedia.metadata.MetadataSessionProgress
import com.theemrock.smoothmedia.metadata.MetadataSessionStatus
import com.theemrock.smoothmedia.metadata.MetadataMissingTrackPageCoordinator
import com.theemrock.smoothmedia.metadata.RoomTrackMetadataRepository
import com.theemrock.smoothmedia.metadata.TrackMetadataEnrichmentUseCase
import com.theemrock.smoothmedia.source.AndroidSelectedTreeParser
import com.theemrock.smoothmedia.source.SourceRegistryFactory
import com.theemrock.smoothmedia.source.SourceRegistryRuntime
import com.theemrock.smoothmedia.source.TreePickerIntentFactory
import com.theemrock.smoothmedia.source.selectRefreshSummary
import com.theemrock.smoothmedia.ui.DeviceOrientationPolicy
import com.theemrock.smoothmedia.ui.SourceManagementScreen
import com.theemrock.smoothmedia.ui.sourceRefreshMessage
import com.theemrock.smoothmedia.ui.sourceRegistrationMessage
import com.theemrock.smoothmedia.ui.sourceRemovalMessage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF19C3E6),
    background = Color(0xFF090B0D),
    surface = Color(0xFF111418),
    onPrimary = Color(0xFF090B0D),
    onBackground = Color(0xFFF4F6F8),
    onSurface = Color(0xFFF4F6F8),
    outline = Color(0xFFD8DEE3),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF19C3E6),
    background = Color(0xFFF5F7F8),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF090B0D),
    onBackground = Color(0xFF111418),
    onSurface = Color(0xFF111418),
    outline = Color(0xFFD8DEE3),
)

class MainActivity : ComponentActivity() {
    private lateinit var runtime: SourceRegistryRuntime
    private lateinit var executor: ExecutorService
    private lateinit var scanController: ManualScanController
    private lateinit var metadataCoordinator: MetadataMissingTrackPageCoordinator
    private lateinit var metadataSessionCoordinator: MetadataContinuousSessionCoordinator
    private var sources by mutableStateOf<List<LibrarySourceEntity>>(emptyList())
    private var statusMessage by mutableStateOf<String?>(null)
    private var scanMessage by mutableStateOf<String?>(null)
    private var scanningSourceId by mutableStateOf<String?>(null)
    private var metadataMissingCount by mutableStateOf(0)
    private var metadataRunning by mutableStateOf(false)
    private var metadataProgress by mutableStateOf<MetadataBatchProgress?>(null)
    private var metadataSessionRunning by mutableStateOf(false)
    private var metadataSessionStatus by mutableStateOf<MetadataSessionStatus?>(null)
    private var metadataSessionProgress by mutableStateOf<MetadataSessionProgress?>(null)
    private var showLibrary by mutableStateOf(false)
    private var showCollections by mutableStateOf(false)
    private lateinit var libraryViewModel: LibraryViewModel
    private lateinit var collectionViewModel: LibraryCollectionViewModel
    private lateinit var libraryRepository: RoomLibraryRepository
    private val metadataCancelRequested = AtomicBoolean(false)
    private val metadataSessionCancelRequested = AtomicBoolean(false)
    private val metadataLaunchGate = MetadataSessionLaunchGate()

    private val treePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val selected = AndroidSelectedTreeParser.fromResult(
            uri = result.data?.data,
            grantedFlags = result.data?.flags ?: 0,
        )
        if (selected == null) {
            statusMessage = getString(R.string.source_invalid_uri)
            return@registerForActivityResult
        }
        submit {
            sourceRegistrationMessage(runtime.registry.register(selected, System.currentTimeMillis()))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = DeviceOrientationPolicy.requestedOrientation(
            resources.configuration.smallestScreenWidthDp,
        )
        enableEdgeToEdge()
        runtime = SourceRegistryFactory.create(this)
        executor = Executors.newSingleThreadExecutor()
        scanController = ManualScanController(runtime.database, contentResolver)
        val metadataRepository = RoomTrackMetadataRepository(runtime.database.trackMetadataDao())
        val metadataEnrichment = TrackMetadataEnrichmentUseCase(
            trackDao = runtime.database.trackDao(),
            bindingDao = runtime.database.locatorBindingDao(),
            exclusionDao = runtime.database.catalogExclusionDao(),
            metadata = metadataRepository,
            reader = AndroidAudioMetadataReader(this),
        )
        val metadataBatch = MetadataBatchCoordinator(metadataEnrichment::enrichTrack)
        metadataCoordinator = MetadataMissingTrackPageCoordinator(
            listMissingTrackIds = runtime.database.trackMetadataDao()::listTrackIdsMissingMetadata,
            batch = metadataBatch,
        )
        metadataSessionCoordinator = MetadataContinuousSessionCoordinator(
            page = metadataCoordinator,
            countMissing = runtime.database.trackMetadataDao()::countTracksMissingMetadata,
        )
        libraryRepository = RoomLibraryRepository(
            runtime.database.libraryDao(),
        )
        libraryViewModel = LibraryViewModel(
            pageLoader = libraryRepository::loadPage,
            totalLoader = libraryRepository::countTracks,
            filteredPageLoader = libraryRepository::loadFilteredPage,
            filteredTotalLoader = libraryRepository::countFilteredTracks,
        )
        collectionViewModel = LibraryCollectionViewModel(
            pageLoader = libraryRepository::loadCollections,
            totalLoader = libraryRepository::countCollections,
        )
        setContent {
            SmoothMediaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showCollections) {
                        LibraryCollectionScreen(
                            state = collectionViewModel.state.value,
                            onNearEnd = { executor.execute { collectionViewModel.loadNextPage() } },
                            onRetry = { executor.execute { collectionViewModel.retry() } },
                            onSongs = {
                                showCollections = false
                                showLibrary = true
                            },
                            onCollection = ::openCollection,
                            onCollectionSelected = { row -> openCollectionSongs(collectionViewModel.state.value.type, row) },
                            onSources = {
                                showCollections = false
                                showLibrary = false
                            },
                        )
                    } else if (showLibrary) {
                        LibraryScreen(
                            state = libraryViewModel.state.value,
                            onNearEnd = { executor.execute { libraryViewModel.loadNextPage() } },
                            onRetry = { executor.execute { libraryViewModel.retry() } },
                            onSources = {
                                showLibrary = false
                                showCollections = false
                            },
                            onCollection = ::openCollection,
                        )
                    } else {
                        SourceManagementScreen(
                            sources = sources,
                            statusMessage = listOfNotNull(statusMessage, scanMessage).joinToString("\n").ifBlank { null },
                            scanningSourceId = scanningSourceId,
                            onAdd = { treePicker.launch(TreePickerIntentFactory.create()) },
                            onRefresh = { refreshSources() },
                            onRemove = { sourceId -> removeSource(sourceId) },
                            onScan = { sourceId -> startScan(sourceId) },
                            onLibrary = {
                                showLibrary = true
                                showCollections = false
                            },
                            metadataMissingCount = metadataMissingCount,
                            metadataRunning = metadataRunning,
                            metadataProgress = metadataProgress,
                            onMetadataStart = { startMetadataPage() },
                            onMetadataStop = { stopMetadataPage() },
                            metadataSessionRunning = metadataSessionRunning,
                            metadataSessionStatus = metadataSessionStatus,
                            metadataSessionProgress = metadataSessionProgress,
                            onMetadataSessionStart = { startMetadataSession() },
                            onMetadataSessionStop = { stopMetadataSession() },
                        )
                    }
                }
            }
        }
        loadAndRefreshSources()
        loadMetadataMissingCount()
        executor.execute { libraryViewModel.loadInitial() }
    }

    override fun onDestroy() {
        metadataCancelRequested.set(true)
        metadataSessionCancelRequested.set(true)
        if (::executor.isInitialized && ::runtime.isInitialized) {
            executor.execute { runtime.close() }
            executor.shutdown()
        }
        super.onDestroy()
    }

    private fun loadAndRefreshSources() {
        executor.execute {
            val stored = runCatching { runtime.repository.listAll() }.getOrElse {
                postState(emptyList(), R.string.source_storage_failed)
                return@execute
            }
            val results = stored.filter { it.selectionState == SourceSelectionState.ACTIVE }.map { source ->
                runtime.registry.refresh(source.sourceId, System.currentTimeMillis())
            }
            val refreshed = runCatching { runtime.repository.listAll() }.getOrElse {
                postState(stored, R.string.source_storage_failed)
                return@execute
            }
            postState(refreshed, selectRefreshSummary(results)?.let(::sourceRefreshMessage))
        }
    }

    private fun refreshSources() {
        executor.execute {
            val activeSources = runCatching { runtime.repository.listAll() }.getOrElse {
                postState(sources, R.string.source_storage_failed)
                return@execute
            }.filter { it.selectionState == SourceSelectionState.ACTIVE }
            val results = activeSources.map { source ->
                runtime.registry.refresh(source.sourceId, System.currentTimeMillis())
            }
            val refreshed = runCatching { runtime.repository.listAll() }.getOrElse {
                postState(activeSources, R.string.source_storage_failed)
                return@execute
            }
            postState(refreshed, selectRefreshSummary(results)?.let(::sourceRefreshMessage))
        }
    }

    private fun removeSource(sourceId: String) {
        submit {
            sourceRemovalMessage(runtime.registry.remove(sourceId, System.currentTimeMillis()))
        }
    }

    private fun startScan(sourceId: String) {
        if (scanningSourceId != null) return
        scanningSourceId = sourceId
        scanMessage = getString(R.string.scan_starting)
        executor.execute {
            val source = runCatching { runtime.repository.find(sourceId) }.getOrNull()
            val result = if (source == null) {
                ManualScanResult.Rejected("", ScanSessionOrchestrator.RejectionReason.SOURCE_NOT_SELECTED)
            } else {
                scanController.start(source, ::postScanProgress)
            }
            runOnUiThread {
                scanningSourceId = null
                scanMessage = scanResultMessage(result)
            }
        }
    }

    private fun startMetadataPage() {
        if (!metadataLaunchGate.tryAcquire()) return
        metadataRunning = true
        metadataSessionStatus = null
        metadataCancelRequested.set(false)
        metadataProgress = null
        executor.execute {
            val result = runCatching {
                metadataCoordinator.runPage(
                    cancellationRequested = metadataCancelRequested::get,
                    onProgress = ::postMetadataProgress,
                )
            }
            runOnUiThread {
                metadataLaunchGate.release()
                if (isDestroyed) return@runOnUiThread
                metadataRunning = false
                result.onFailure { metadataProgress = null }
                result.getOrNull()?.let { metadataProgress = it.batch.progress }
                loadMetadataMissingCount()
            }
        }
    }

    private fun stopMetadataPage() {
        metadataCancelRequested.set(true)
    }

    private fun startMetadataSession() {
        if (!metadataLaunchGate.tryAcquire()) return
        metadataSessionRunning = true
        metadataSessionStatus = null
        metadataSessionProgress = MetadataSessionProgress.empty()
        metadataSessionCancelRequested.set(false)
        executor.execute {
            val result = runCatching {
                metadataSessionCoordinator.runSession(
                    cancellationRequested = metadataSessionCancelRequested::get,
                    onProgress = ::postMetadataSessionProgress,
                )
            }.getOrElse { error ->
                MetadataContinuousSessionResult(
                    status = MetadataSessionStatus.ERROR,
                    progress = metadataSessionProgress ?: MetadataSessionProgress.empty(),
                    errorMessage = error.message,
                )
            }
            metadataLaunchGate.release()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                metadataSessionRunning = false
                metadataSessionStatus = result.status
                metadataSessionProgress = result.progress
                loadMetadataMissingCount()
            }
        }
    }

    private fun stopMetadataSession() {
        metadataSessionCancelRequested.set(true)
    }

    private fun openCollection(type: LibraryCollectionType) {
        showCollections = true
        showLibrary = false
        executor.execute { collectionViewModel.loadInitial(type) }
    }

    private fun openCollectionSongs(type: LibraryCollectionType, row: LibraryCollectionRow) {
        val filter = when (type) {
            LibraryCollectionType.ALBUMS -> LibraryTrackFilter.Album(row.title, row.subtitle.orEmpty())
            LibraryCollectionType.ARTISTS -> LibraryTrackFilter.Artist(row.title)
            LibraryCollectionType.GENRES -> LibraryTrackFilter.Genre(row.title)
            LibraryCollectionType.FOLDERS -> LibraryTrackFilter.Folder(row.key)
        }
        showCollections = false
        showLibrary = true
        executor.execute { libraryViewModel.loadInitial(filter, row.title) }
    }

    private fun postMetadataProgress(progress: MetadataBatchProgress) {
        runOnUiThread {
            if (!isDestroyed) metadataProgress = progress
        }
    }

    private fun postMetadataSessionProgress(progress: MetadataSessionProgress) {
        runOnUiThread {
            if (!isDestroyed) metadataSessionProgress = progress
        }
    }

    private fun loadMetadataMissingCount() {
        if (!::executor.isInitialized || !::runtime.isInitialized) return
        executor.execute {
            val count = runCatching { runtime.database.trackMetadataDao().countTracksMissingMetadata() }
                .getOrDefault(0)
            runOnUiThread {
                if (!isDestroyed) metadataMissingCount = count
            }
        }
    }

    private fun postScanProgress(progress: ManualScanProgress) {
        runOnUiThread {
            if (isDestroyed) return@runOnUiThread
            scanMessage = getString(
                R.string.scan_progress,
                progress.processedCount,
                progress.discoveredCount,
                progress.errorCount,
            )
        }
    }

    private fun scanResultMessage(result: ManualScanResult): String = when (result) {
        is ManualScanResult.Completed -> getString(
            R.string.scan_completed,
            result.processedCount,
            result.successfulCount,
            result.skippedUnreadableCount,
        )
        is ManualScanResult.Paused -> getString(R.string.scan_paused)
        is ManualScanResult.Cancelled -> getString(R.string.scan_cancelled)
        is ManualScanResult.AlreadyActive -> getString(R.string.scan_already_active)
        is ManualScanResult.DiscoveryFailed -> getString(R.string.scan_discovery_failed)
        is ManualScanResult.CandidateFailed -> getString(R.string.scan_candidate_failed, result.reason.name)
        is ManualScanResult.Rejected -> getString(R.string.scan_rejected)
    }

    private fun submit(operation: () -> Int) {
        executor.execute {
            val message = runCatching(operation).getOrElse { R.string.source_storage_failed }
            val updatedSources = runCatching { runtime.repository.listAll() }.getOrElse { sources }
            postState(updatedSources, message)
        }
    }

    private fun postState(updatedSources: List<LibrarySourceEntity>, messageRes: Int?) {
        runOnUiThread {
            if (isDestroyed) return@runOnUiThread
            sources = updatedSources
            statusMessage = messageRes?.let(::getString)
        }
    }
}

@Composable
private fun SmoothMediaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
