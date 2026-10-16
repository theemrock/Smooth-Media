package com.theemrock.smoothmedia

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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
import com.theemrock.smoothmedia.library.LibrarySearchViewModel
import com.theemrock.smoothmedia.library.LibrarySong
import com.theemrock.smoothmedia.library.LibraryMetadataState
import com.theemrock.smoothmedia.library.LibraryLocationState
import com.theemrock.smoothmedia.playback.CatalogPlaybackLocationResolver
import com.theemrock.smoothmedia.playback.PlaybackEngineState
import com.theemrock.smoothmedia.playback.PlaybackSessionClient
import com.theemrock.smoothmedia.playback.PlaybackQueueController
import com.theemrock.smoothmedia.playback.QueueItem
import com.theemrock.smoothmedia.playback.RepeatMode
import com.theemrock.smoothmedia.playback.PlaybackQueueStore
import com.theemrock.smoothmedia.playback.QueueMode
import com.theemrock.smoothmedia.artwork.AndroidArtworkResolver
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
import com.theemrock.smoothmedia.ui.LibrarySearchScreen
import com.theemrock.smoothmedia.ui.NowPlayingScreen
import com.theemrock.smoothmedia.ui.EqualizerScreen
import com.theemrock.smoothmedia.ui.QueueScreen
import com.theemrock.smoothmedia.eq.AdaptiveEqState
import com.theemrock.smoothmedia.eq.EqualizerBand
import com.theemrock.smoothmedia.eq.EqualizerProfileStore
import com.theemrock.smoothmedia.ui.sourceRefreshMessage
import com.theemrock.smoothmedia.ui.sourceRegistrationMessage
import com.theemrock.smoothmedia.ui.sourceRemovalMessage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

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
    private var showSearch by mutableStateOf(false)
    private var showNowPlaying by mutableStateOf(false)
    private var showEqualizer by mutableStateOf(false)
    private var showQueue by mutableStateOf(false)
    private var nowPlayingSong by mutableStateOf<LibrarySong?>(null)
    private var playbackState by mutableStateOf(PlaybackEngineState())
    private var equalizerBands by mutableStateOf<List<EqualizerBand>>(emptyList())
    private var manualEqualizerLevels by mutableStateOf<Map<Short, Int>>(emptyMap())
    private var manualEqualizerEnabled by mutableStateOf(true)
    private var lastPlaybackEntryId: String? = null
    private var lastPersistedPlaybackPosition = 0L
    private lateinit var equalizerProfileStore: EqualizerProfileStore
    private lateinit var libraryViewModel: LibraryViewModel
    private lateinit var collectionViewModel: LibraryCollectionViewModel
    private lateinit var libraryRepository: RoomLibraryRepository
    private lateinit var artworkResolver: AndroidArtworkResolver
    private lateinit var searchViewModel: LibrarySearchViewModel
    private lateinit var playbackClient: PlaybackSessionClient
    private val queueController = PlaybackQueueController()
    private lateinit var queueStore: PlaybackQueueStore
    private var filteredCollectionType by mutableStateOf<LibraryCollectionType?>(null)
    private lateinit var libraryBackCallback: OnBackPressedCallback
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
        equalizerProfileStore = EqualizerProfileStore(this)
        manualEqualizerLevels = equalizerProfileStore.readLevels()
        manualEqualizerEnabled = equalizerProfileStore.isManualEnabled()
        queueStore = PlaybackQueueStore(this)
        queueController.restore(queueStore.read())
        playbackClient = PlaybackSessionClient(
            this,
            onState = { state ->
                val wasPlaying = playbackState.status == com.theemrock.smoothmedia.playback.PlaybackStatus.PLAYING
                playbackState = state
                queueController.setPosition(state.positionMs)
                val bands = playbackClient.equalizerBands()
                if (bands.isNotEmpty()) equalizerBands = bands
                if (wasPlaying && state.status != com.theemrock.smoothmedia.playback.PlaybackStatus.PLAYING) {
                    persistQueue()
                    lastPersistedPlaybackPosition = state.positionMs
                }
            },
            onQueueState = { entryId, position ->
                queueController.syncCurrentEntry(entryId, position)
                val entryChanged = entryId != null && entryId != lastPlaybackEntryId
                if (entryChanged) {
                    lastPlaybackEntryId = entryId
                    val index = queueController.state.value.items.indexOfFirst { it.entryId == entryId }
                    if (index >= 0) updateNowPlayingItem(index)
                }
                if (entryChanged || abs(position - lastPersistedPlaybackPosition) >= 5_000L) {
                    persistQueue()
                    lastPersistedPlaybackPosition = position
                }
            },
        )
        libraryBackCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val type = filteredCollectionType
                if (showLibrary && type != null) {
                    returnToCollections(type)
                    return
                }
                val next = com.theemrock.smoothmedia.ui.MainNavigationBack.handle(
                    com.theemrock.smoothmedia.ui.MainNavigationState(
                        showLibrary = showLibrary,
                        showCollections = showCollections,
                        showSearch = showSearch,
                        showNowPlaying = showNowPlaying,
                        showEqualizer = showEqualizer,
                        showQueue = showQueue,
                    ),
                )
                if (next == null) finish() else {
                    showLibrary = next.showLibrary
                    showCollections = next.showCollections
                    showSearch = next.showSearch
                    showNowPlaying = next.showNowPlaying
                    showEqualizer = next.showEqualizer
                    showQueue = next.showQueue
                    updateLibraryBackCallback()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, libraryBackCallback)
        val metadataRepository = RoomTrackMetadataRepository(runtime.database.trackMetadataDao())
        val metadataEnrichment = TrackMetadataEnrichmentUseCase(
            trackDao = runtime.database.trackDao(),
            bindingDao = runtime.database.locatorBindingDao(),
            exclusionDao = runtime.database.catalogExclusionDao(),
            metadata = metadataRepository,
            reader = AndroidAudioMetadataReader(this),
        )
        scanController = ManualScanController(
            database = runtime.database,
            contentResolver = contentResolver,
            enrichMetadata = metadataEnrichment::enrichTrack,
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
        artworkResolver = AndroidArtworkResolver(contentResolver, libraryRepository)
        searchViewModel = LibrarySearchViewModel(libraryRepository)
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
                    if (showQueue) {
                        QueueScreen(
                            state = queueController.state.value,
                            onBack = { showQueue = false },
                            onRemove = { queueController.remove(it); persistQueue(); syncQueueToSession(playbackState.status == com.theemrock.smoothmedia.playback.PlaybackStatus.PLAYING) },
                            onClear = {
                                queueController.clear()
                                playbackClient.clearQueue()
                                persistQueue()
                                nowPlayingSong = null
                                playbackState = PlaybackEngineState()
                            },
                            onMoveUp = { index -> queueController.reorder(index, index - 1); persistQueue(); syncQueueToSession(playbackState.status == com.theemrock.smoothmedia.playback.PlaybackStatus.PLAYING, preservePosition = true) },
                            onMoveDown = { index -> queueController.reorder(index, index + 1); persistQueue(); syncQueueToSession(playbackState.status == com.theemrock.smoothmedia.playback.PlaybackStatus.PLAYING, preservePosition = true) },
                            onSelect = ::playQueueIndex,
                            onNext = ::playNext,
                            onPrevious = ::playPrevious,
                            onShuffle = {
                                queueController.setShuffle(!queueController.state.value.shuffle)
                                playbackClient.setShuffle(queueController.state.value.shuffle)
                                persistQueue()
                                syncQueueToSession(playWhenReady = playbackState.status == com.theemrock.smoothmedia.playback.PlaybackStatus.PLAYING, preservePosition = true)
                            },
                            onRepeat = {
                                val next = when (queueController.state.value.repeat) {
                                    RepeatMode.OFF -> RepeatMode.ALL
                                    RepeatMode.ALL -> RepeatMode.ONE
                                    RepeatMode.ONE -> RepeatMode.OFF
                                }
                                queueController.setRepeat(next)
                                playbackClient.setRepeat(next)
                                persistQueue()
                            },
                        )
                    } else if (showEqualizer) {
                        EqualizerScreen(
                            adaptiveState = AdaptiveEqState.fromMeasuredLevels(emptyMap()),
                            manualLevels = manualEqualizerLevels,
                            bands = equalizerBands,
                            enabled = manualEqualizerEnabled,
                            onEnabledChanged = { enabled ->
                                manualEqualizerEnabled = enabled
                                equalizerProfileStore.writeManualEnabled(enabled)
                                playbackClient.setEqualizerEnabled(enabled)
                            },
                            onReset = {
                                val flat = equalizerBands.associate { it.id to 0 }
                                manualEqualizerLevels = flat
                                playbackClient.applyManualEqualizer(flat)
                            },
                            onManualLevelChanged = { band, level ->
                                val next = manualEqualizerLevels + (band to level)
                                manualEqualizerLevels = next
                                equalizerProfileStore.writeLevels(next)
                                playbackClient.applyManualEqualizer(next)
                            },
                            onBack = { showEqualizer = false },
                        )
                    } else if (showNowPlaying && nowPlayingSong != null) {
                        NowPlayingScreen(
                            song = nowPlayingSong!!,
                            state = playbackState,
                            onBack = { showNowPlaying = false },
                            onPause = playbackClient::pause,
                            onResume = playbackClient::resume,
                            onSeek = playbackClient::seekTo,
                            onEqualizer = { showEqualizer = true },
                            onQueue = { showQueue = true },
                            onNext = ::playNext,
                            onPrevious = ::playPrevious,
                            onShuffle = {
                                queueController.setShuffle(!queueController.state.value.shuffle)
                                playbackClient.setShuffle(queueController.state.value.shuffle)
                                persistQueue()
                                syncQueueToSession(playWhenReady = playbackState.status == com.theemrock.smoothmedia.playback.PlaybackStatus.PLAYING, preservePosition = true)
                            },
                            onRepeat = {
                                val next = when (queueController.state.value.repeat) {
                                    RepeatMode.OFF -> RepeatMode.ALL
                                    RepeatMode.ALL -> RepeatMode.ONE
                                    RepeatMode.ONE -> RepeatMode.OFF
                                }
                                queueController.setRepeat(next)
                                playbackClient.setRepeat(next)
                                persistQueue()
                            },
                            artworkResolver = artworkResolver,
                        )
                    } else if (showSearch) {
                        LibrarySearchScreen(
                            state = searchViewModel.state.value,
                            onQueryChanged = searchViewModel::onQueryChanged,
                            onClear = searchViewModel::clear,
                            onSong = { song ->
                                val visibleSongs = searchViewModel.state.value.results.songs
                                playSong(song, visibleSongs, com.theemrock.smoothmedia.library.LibrarySearchQuery.snapshotKey(searchViewModel.state.value.query, visibleSongs))
                            },
                            onBack = { showSearch = false },
                        )
                    } else if (showCollections) {
                        LibraryCollectionScreen(
                            state = collectionViewModel.state.value,
                            onNearEnd = { executor.execute { collectionViewModel.loadNextPage() } },
                            onRetry = { executor.execute { collectionViewModel.retry() } },
                            onSongs = {
                                showAllSongs()
                            },
                            onCollection = ::openCollection,
                            onCollectionSelected = { row -> openCollectionSongs(collectionViewModel.state.value.type, row) },
                            onSources = {
                                showCollections = false
                                showLibrary = false
                                updateLibraryBackCallback()
                            },
                            artworkResolver = artworkResolver,
                        )
                    } else if (showLibrary) {
                        LibraryScreen(
                            state = libraryViewModel.state.value,
                            onNearEnd = { executor.execute { libraryViewModel.loadNextPage() } },
                            onRetry = { executor.execute { libraryViewModel.retry() } },
                            onSources = {
                                showLibrary = false
                                showCollections = false
                                updateLibraryBackCallback()
                            },
                            onSongs = ::showAllSongs,
                            onCollection = ::openCollection,
                            onSearch = { showSearch = true },
                            onSong = { song -> playSong(song) },
                            playbackState = playbackState.takeIf { nowPlayingSong != null },
                            miniPlayerSong = nowPlayingSong,
                            onMiniPlayerOpen = { showNowPlaying = true },
                            onMiniPlayerPause = playbackClient::pause,
                            onMiniPlayerResume = playbackClient::resume,
                            onMiniPlayerNext = ::playNext,
                            onAddToQueue = ::addToQueue,
                            onPlayNext = ::playNextSong,
                            artworkResolver = artworkResolver,
                            initialScrollIndex = libraryViewModel.scrollIndex,
                            initialScrollOffset = libraryViewModel.scrollOffset,
                            onScrollPositionChanged = libraryViewModel::saveScrollPosition,
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
                                showAllSongs()
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
                            showStandaloneMetadataControls = false,
                        )
                    }
                }
            }
        }
        loadAndRefreshSources()
        loadMetadataMissingCount()
        executor.execute { libraryViewModel.loadInitial() }
        executor.execute { syncQueueToSession(playWhenReady = false) }
    }

    override fun onDestroy() {
        metadataCancelRequested.set(true)
        metadataSessionCancelRequested.set(true)
        persistQueue()
        if (::playbackClient.isInitialized) playbackClient.close()
        if (::searchViewModel.isInitialized) searchViewModel.close()
        if (::executor.isInitialized && ::runtime.isInitialized) {
            executor.execute { runtime.close() }
            executor.shutdown()
        }
        super.onDestroy()
    }

    private fun playSong(song: LibrarySong, sourceSongs: List<LibrarySong>? = null, sourceSnapshotKey: String? = null) {
        executor.execute {
            val location = CatalogPlaybackLocationResolver(runtime.database).resolve(song.trackId).firstOrNull()
            val source = sourceSongs?.takeIf { list -> list.any { it.trackId == song.trackId } }
                ?: libraryViewModel.snapshotCurrentList().ifEmpty { listOf(song) }
            val selectedIndex = source.indexOfFirst { it.trackId == song.trackId }.coerceAtLeast(0)
            val items = source.map { entry ->
                QueueItem(
                    trackId = entry.trackId,
                    title = entry.title,
                    artist = entry.artist,
                    album = entry.album,
                    durationMs = entry.durationMs,
                )
            }
            runOnUiThread {
                nowPlayingSong = song
                showNowPlaying = true
                if (location != null) {
                    queueController.startGhost(items, selectedIndex, sourceSnapshotKey ?: libraryViewModel.currentSnapshotKey())
                    persistQueue()
                    syncQueueToSession(playWhenReady = true)
                } else playbackState = PlaybackEngineState(
                    status = com.theemrock.smoothmedia.playback.PlaybackStatus.ERROR,
                    trackId = song.trackId,
                    errorMessage = "No accessible SAF location",
                )
            }
        }
    }

    private fun addToQueue(song: LibrarySong) = enqueueSong(song, playNext = false)

    private fun playNextSong(song: LibrarySong) = enqueueSong(song, playNext = true)

    private fun enqueueSong(song: LibrarySong, playNext: Boolean) {
        if (!song.isAccessible) return
        executor.execute {
            val location = CatalogPlaybackLocationResolver(runtime.database).resolve(song.trackId).firstOrNull()
            runOnUiThread {
                if (location == null) return@runOnUiThread
                val item = QueueItem(trackId = song.trackId, title = song.title, artist = song.artist, album = song.album, durationMs = song.durationMs)
                val added = if (queueController.state.value.mode == QueueMode.GHOST) {
                    queueController.takeOverUserQueue(item, playNext)
                } else if (playNext) {
                    queueController.playNext(item)
                } else {
                    queueController.add(item)
                }
                if (added) {
                    persistQueue()
                    syncQueueToSession(
                        playWhenReady = playbackState.status == com.theemrock.smoothmedia.playback.PlaybackStatus.PLAYING,
                        preservePosition = true,
                    )
                }
            }
        }
    }

    private fun playQueueIndex(index: Int) {
        if (!queueController.select(index)) return
        showQueueItem(index)
        persistQueue()
        syncQueueToSession(playWhenReady = true)
    }

    private fun playNext() {
        val next = queueController.next() ?: return
        showQueueItem(next)
        persistQueue()
        syncQueueToSession(playWhenReady = true)
    }

    private fun playPrevious() {
        val previous = queueController.previous() ?: return
        showQueueItem(previous)
        persistQueue()
        syncQueueToSession(playWhenReady = true)
    }

    private fun syncQueueToSession(playWhenReady: Boolean, preservePosition: Boolean = false) {
        val items = queueController.orderedForPlayback()
        if (items.isEmpty()) return
        val currentEntryId = queueController.state.value.current?.entryId
        val startIndex = items.indexOfFirst { it.entryId == currentEntryId }.coerceAtLeast(0)
        executor.execute {
            val resolver = CatalogPlaybackLocationResolver(runtime.database)
            val locations = items.mapNotNull { item -> resolver.resolve(item.trackId).firstOrNull()?.let { item.entryId to it } }.toMap()
            runOnUiThread { playbackClient.loadQueue(items, locations, startIndex = startIndex, startPositionMs = if (preservePosition) playbackState.positionMs else 0L, playWhenReady = playWhenReady) }
        }
    }

    private fun showQueueItem(index: Int) {
        updateNowPlayingItem(index)
        showNowPlaying = true
    }

    private fun updateNowPlayingItem(index: Int) {
        val item = queueController.state.value.items.getOrNull(index) ?: return
        nowPlayingSong = LibrarySong(
            trackId = item.trackId,
            title = item.title.ifBlank { item.trackId },
            artist = item.artist,
            album = item.album,
            durationMs = item.durationMs,
            metadataState = LibraryMetadataState.COMPLETE,
            locationState = LibraryLocationState.AVAILABLE,
            locationCount = 1,
        )
    }

    private fun persistQueue() {
        if (::queueStore.isInitialized) queueStore.write(queueController.state.value)
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
        filteredCollectionType = null
        updateLibraryBackCallback()
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
        filteredCollectionType = type
        updateLibraryBackCallback()
        executor.execute { libraryViewModel.loadInitial(filter, row.title) }
    }

    private fun showAllSongs() {
        if (showLibrary && filteredCollectionType == null && libraryViewModel.state.value.loaded) {
            updateLibraryBackCallback()
            return
        }
        showCollections = false
        showLibrary = true
        filteredCollectionType = null
        updateLibraryBackCallback()
        executor.execute { libraryViewModel.loadInitial(null, "Your library") }
    }

    private fun returnToCollections(type: LibraryCollectionType) {
        showCollections = true
        showLibrary = false
        filteredCollectionType = null
        updateLibraryBackCallback()
        if (collectionViewModel.state.value.type != type || !collectionViewModel.state.value.loaded) {
            executor.execute { collectionViewModel.loadInitial(type) }
        }
    }

    private fun updateLibraryBackCallback() {
        if (::libraryBackCallback.isInitialized) {
            libraryBackCallback.isEnabled = true
        }
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
                progress.enrichedCount,
                progress.metadataFailedCount,
            )
        }
    }

    private fun scanResultMessage(result: ManualScanResult): String = when (result) {
        is ManualScanResult.Completed -> getString(
            R.string.scan_completed,
            result.processedCount,
            result.successfulCount,
            result.skippedUnreadableCount,
            result.enrichedCount,
            result.metadataFailedCount,
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
