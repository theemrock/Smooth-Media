package com.theemrock.smoothmedia

import android.app.Activity
import android.os.Bundle
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import com.theemrock.smoothmedia.catalog.database.entity.MetadataRecoverySessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.MetadataRecoverySessionStatus
import com.theemrock.smoothmedia.catalog.database.entity.PlaylistEntity
import com.theemrock.smoothmedia.catalog.scan.ManualScanController
import com.theemrock.smoothmedia.catalog.scan.ManualScanProgress
import com.theemrock.smoothmedia.catalog.scan.ManualScanResult
import com.theemrock.smoothmedia.catalog.scan.ScanSessionOrchestrator
import com.theemrock.smoothmedia.catalog.scan.ScanPhase
import com.theemrock.smoothmedia.library.LibraryViewModel
import com.theemrock.smoothmedia.library.LibraryCollectionType
import com.theemrock.smoothmedia.library.LibraryGenreAssignmentUi
import com.theemrock.smoothmedia.library.LibraryCollectionViewModel
import com.theemrock.smoothmedia.library.LibraryCollectionRow
import com.theemrock.smoothmedia.library.LibraryTrackFilter
import com.theemrock.smoothmedia.library.AlbumGroupingKey
import com.theemrock.smoothmedia.library.RoomLibraryRepository
import com.theemrock.smoothmedia.library.LibrarySearchViewModel
import com.theemrock.smoothmedia.library.LibrarySong
import com.theemrock.smoothmedia.library.LibraryMetadataState
import com.theemrock.smoothmedia.library.LibraryLocationState
import com.theemrock.smoothmedia.library.deduplicateLibrarySongs
import com.theemrock.smoothmedia.library.LibraryDashboardData
import com.theemrock.smoothmedia.library.LibraryArtistAlbumPreview
import com.theemrock.smoothmedia.library.LibraryGenreArtworkPreview
import com.theemrock.smoothmedia.library.SourceTrackCountSnapshot
import com.theemrock.smoothmedia.genre.RoomGenreRepository
import com.theemrock.smoothmedia.playback.CatalogPlaybackLocationResolver
import com.theemrock.smoothmedia.playback.PlaybackEngineState
import com.theemrock.smoothmedia.playback.PlaybackSessionClient
import com.theemrock.smoothmedia.playback.PlaybackQueueController
import com.theemrock.smoothmedia.playback.QueueItem
import com.theemrock.smoothmedia.playback.RepeatMode
import com.theemrock.smoothmedia.playback.PlaybackQueueStore
import com.theemrock.smoothmedia.playback.PlaybackWindow
import com.theemrock.smoothmedia.playback.PlaybackWindowResolver
import com.theemrock.smoothmedia.playback.QueueMode
import com.theemrock.smoothmedia.playlist.PlaylistRepository
import com.theemrock.smoothmedia.playlist.orderedUniqueTrackIds
import com.theemrock.smoothmedia.artwork.AndroidArtworkResolver
import com.theemrock.smoothmedia.artwork.ArtworkDiskCache
import com.theemrock.smoothmedia.artwork.ArtworkRepository
import com.theemrock.smoothmedia.ui.LibraryScreen
import com.theemrock.smoothmedia.ui.LibraryDashboardScreen
import com.theemrock.smoothmedia.ui.LibraryCollectionScreen
import com.theemrock.smoothmedia.ui.LibraryAlbumDetailData
import com.theemrock.smoothmedia.ui.LibraryAlbumDetailScreen
import com.theemrock.smoothmedia.ui.LibraryDetailScreen
import com.theemrock.smoothmedia.ui.PlaylistPickerDialog
import com.theemrock.smoothmedia.ui.LyricsScreen
import com.theemrock.smoothmedia.lyrics.CachedLyrics
import com.theemrock.smoothmedia.lyrics.RoomLyricsCache
import com.theemrock.smoothmedia.lyrics.LyricsNavigationPolicy
import com.theemrock.smoothmedia.metadata.AndroidAudioMetadataReader
import com.theemrock.smoothmedia.metadata.MetadataBatchProgress
import com.theemrock.smoothmedia.metadata.MetadataBatchCoordinator
import com.theemrock.smoothmedia.metadata.MetadataContinuousSessionCoordinator
import com.theemrock.smoothmedia.metadata.MetadataContinuousSessionResult
import com.theemrock.smoothmedia.metadata.MetadataSessionLaunchGate
import com.theemrock.smoothmedia.metadata.MetadataSessionProgress
import com.theemrock.smoothmedia.metadata.MetadataSessionStatus
import com.theemrock.smoothmedia.metadata.MetadataMissingTrackPageCoordinator
import com.theemrock.smoothmedia.metadata.MetadataRefreshPreviewCoordinator
import com.theemrock.smoothmedia.metadata.MetadataRefreshPreviewResult
import com.theemrock.smoothmedia.metadata.MetadataRecoveryEngine
import com.theemrock.smoothmedia.metadata.MetadataRecoverySessionCoordinator
import com.theemrock.smoothmedia.metadata.resolveMetadataMissingCount
import com.theemrock.smoothmedia.metadata.RoomMetadataApplyDryRun
import com.theemrock.smoothmedia.metadata.RoomMetadataApplyDryRunSummary
import com.theemrock.smoothmedia.metadata.RoomMetadataApplySessionStore
import com.theemrock.smoothmedia.metadata.RoomMetadataApplyExecutor
import com.theemrock.smoothmedia.metadata.ControlledMetadataRoomWriter
import com.theemrock.smoothmedia.metadata.MetadataAutonomousApplyCoordinator
import com.theemrock.smoothmedia.metadata.MetadataAutonomousApplyDecision
import com.theemrock.smoothmedia.metadata.MetadataAutonomousSettings
import com.theemrock.smoothmedia.metadata.SharedPreferencesMetadataAutonomousSettings
import com.theemrock.smoothmedia.metadata.RoomTrackMetadataRepository
import com.theemrock.smoothmedia.metadata.TrackMetadataRepository
import com.theemrock.smoothmedia.metadata.TrackMetadataEnrichmentUseCase
import com.theemrock.smoothmedia.metadata.GenreMetadataBackfill
import com.theemrock.smoothmedia.metadata.MetadataEditorCoordinator
import com.theemrock.smoothmedia.metadata.MetadataEditorState
import com.theemrock.smoothmedia.source.AndroidSelectedTreeParser
import com.theemrock.smoothmedia.source.SourceRegistryFactory
import com.theemrock.smoothmedia.source.SourceRegistryRuntime
import com.theemrock.smoothmedia.source.TreePickerIntentFactory
import com.theemrock.smoothmedia.source.selectRefreshSummary
import com.theemrock.smoothmedia.ui.DeviceOrientationPolicy
import com.theemrock.smoothmedia.ui.SourceManagementScreen
import com.theemrock.smoothmedia.ui.SettingsScreen
import com.theemrock.smoothmedia.ui.LibraryManagementScreen
import com.theemrock.smoothmedia.ui.LibrarySearchScreen
import com.theemrock.smoothmedia.ui.MetadataEditorScreen
import com.theemrock.smoothmedia.ui.NowPlayingScreen
import com.theemrock.smoothmedia.ui.EqualizerScreen
import com.theemrock.smoothmedia.ui.QueueScreen
import com.theemrock.smoothmedia.eq.AdaptiveEqState
import com.theemrock.smoothmedia.eq.EqualizerBand
import com.theemrock.smoothmedia.eq.EqualizerProfileStore
import com.theemrock.smoothmedia.ui.sourceRefreshMessage
import com.theemrock.smoothmedia.ui.sourceRegistrationMessage
import com.theemrock.smoothmedia.ui.sourceRemovalMessage
import com.theemrock.smoothmedia.ui.SmoothMediaColors
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

private val DarkColorScheme = darkColorScheme(
    primary = SmoothMediaColors.Accent,
    background = SmoothMediaColors.Background,
    surface = SmoothMediaColors.Surface,
    onPrimary = SmoothMediaColors.Background,
    onBackground = SmoothMediaColors.TextPrimary,
    onSurface = SmoothMediaColors.TextPrimary,
    outline = SmoothMediaColors.Divider,
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

private enum class LibraryBackTarget { DASHBOARD, LIBRARY, COLLECTIONS, DETAIL }

class MainActivity : ComponentActivity() {
    private lateinit var runtime: SourceRegistryRuntime
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var executor: ExecutorService
    private lateinit var navigationExecutor: ExecutorService
    private lateinit var scanControlExecutor: ExecutorService
    private lateinit var scanController: ManualScanController
    private lateinit var metadataCoordinator: MetadataMissingTrackPageCoordinator
    private lateinit var metadataSessionCoordinator: MetadataContinuousSessionCoordinator
    private lateinit var metadataEnrichment: TrackMetadataEnrichmentUseCase
    private lateinit var metadataRepository: TrackMetadataRepository
    private lateinit var metadataPreviewCoordinator: MetadataRefreshPreviewCoordinator
    private lateinit var metadataRecoveryCoordinator: MetadataRecoverySessionCoordinator
    private lateinit var metadataAutonomousSettings: MetadataAutonomousSettings
    private lateinit var metadataAutonomousCoordinator: MetadataAutonomousApplyCoordinator
    private var sources by mutableStateOf<List<LibrarySourceEntity>>(emptyList())
    private var sourceTrackCounts by mutableStateOf<Map<String, Int>>(emptyMap())
    private var statusMessage by mutableStateOf<String?>(null)
    private var scanMessage by mutableStateOf<String?>(null)
    private var scanningSourceId by mutableStateOf<String?>(null)
    private var scanSessionId by mutableStateOf<String?>(null)
    private var scanPhase by mutableStateOf(ScanPhase.IDLE)
    private var scanProgress by mutableStateOf<ManualScanProgress?>(null)
    private var scanExecutionAttached by mutableStateOf(false)
    private var metadataMissingCount by mutableStateOf(0)
    private var metadataRunning by mutableStateOf(false)
    private var metadataProgress by mutableStateOf<MetadataBatchProgress?>(null)
    private var metadataSessionRunning by mutableStateOf(false)
    private var metadataSessionStatus by mutableStateOf<MetadataSessionStatus?>(null)
    private var metadataSessionProgress by mutableStateOf<MetadataSessionProgress?>(null)
    private var metadataPreviewRunning by mutableStateOf(false)
    private var metadataPreview by mutableStateOf<MetadataRefreshPreviewResult?>(null)
    private var metadataRecoveryStatus by mutableStateOf<MetadataRecoverySessionStatus?>(null)
    private var metadataRecoveryProgress by mutableStateOf<MetadataRecoverySessionEntity?>(null)
    private var metadataRecoverySessionId by mutableStateOf<String?>(null)
    private var metadataApplyDryRun by mutableStateOf<RoomMetadataApplyDryRunSummary?>(null)
    private var metadataApplyDryRunRunning by mutableStateOf(false)
    private var metadataApplyStatus by mutableStateOf<String?>(null)
    private var metadataApplyRunning by mutableStateOf(false)
    private var metadataAutonomousEnabled by mutableStateOf(false)
    private var metadataAutonomousToggleEnabled by mutableStateOf(false)
    private var metadataAutonomousStatus by mutableStateOf<String?>(null)
    private val navigationGeneration = AtomicLong(0)
    private val collectionPageLoadInFlight = AtomicBoolean(false)
    private var genreBackfillRunning by mutableStateOf(false)
    private var genreBackfillStatus by mutableStateOf<String?>(null)
    private lateinit var genreMetadataBackfill: GenreMetadataBackfill
    private lateinit var metadataEditorCoordinator: MetadataEditorCoordinator
    private val metadataAutonomousTriggerRunning = AtomicBoolean(false)
    private var dashboardData by mutableStateOf<LibraryDashboardData?>(null)
    private var showDashboard by mutableStateOf(true)
    private var showLibrary by mutableStateOf(false)
    private var showCollections by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var showLibraryManagement by mutableStateOf(false)
    private var showSourceManagement by mutableStateOf(false)
    private var showAlbumDetail by mutableStateOf(false)
    private var showLibraryDetail by mutableStateOf(false)
    private var showSearch by mutableStateOf(false)
    private var showNowPlaying by mutableStateOf(false)
    private var showLyrics by mutableStateOf(false)
    private var currentLyrics by mutableStateOf<CachedLyrics?>(null)
    private var showEqualizer by mutableStateOf(false)
    private var showQueue by mutableStateOf(false)
    private var showMetadataEditor by mutableStateOf(false)
    private var showPlaylistPicker by mutableStateOf(false)
    private var playlistPickerSongs by mutableStateOf<List<LibrarySong>>(emptyList())
    private var playlistOptions by mutableStateOf<List<PlaylistEntity>>(emptyList())
    private var metadataEditorState by mutableStateOf(MetadataEditorState(emptyList()))
    private var metadataEditorItems by mutableStateOf(emptyList<com.theemrock.smoothmedia.metadata.MetadataEditorBatchItem>())
    private var metadataEditorSaving by mutableStateOf(false)
    private var metadataEditorProgress by mutableStateOf<String?>(null)
    private var metadataEditorError by mutableStateOf<String?>(null)
    private val metadataEditorCancelRequested = AtomicBoolean(false)
    private val metadataEditorGeneration = AtomicLong(0)
    private var nowPlayingSong by mutableStateOf<LibrarySong?>(null)
    private var playbackState by mutableStateOf(PlaybackEngineState())
    private var equalizerBands by mutableStateOf<List<EqualizerBand>>(emptyList())
    private var manualEqualizerLevels by mutableStateOf<Map<Short, Int>>(emptyMap())
    private var manualEqualizerEnabled by mutableStateOf(true)
    private var lastPlaybackEntryId: String? = null
    private var lastPersistedPlaybackPosition = 0L
    private var activeGhostPlaybackWindowEntryIds: List<String> = emptyList()
    private lateinit var equalizerProfileStore: EqualizerProfileStore
    private lateinit var libraryViewModel: LibraryViewModel
    private lateinit var collectionViewModel: LibraryCollectionViewModel
    private lateinit var libraryRepository: RoomLibraryRepository
    private lateinit var lyricsCache: RoomLyricsCache
    private lateinit var artworkResolver: AndroidArtworkResolver
    private lateinit var searchViewModel: LibrarySearchViewModel
    private lateinit var playbackClient: PlaybackSessionClient
    private val queueController = PlaybackQueueController()
    private lateinit var queueStore: PlaybackQueueStore
    private var filteredCollectionType by mutableStateOf<LibraryCollectionType?>(null)
    private var albumDetailData by mutableStateOf<LibraryAlbumDetailData?>(null)
    private var libraryDetailType by mutableStateOf<LibraryCollectionType?>(null)
    private var libraryDetailRow by mutableStateOf<LibraryCollectionRow?>(null)
    private var libraryDetailSongs by mutableStateOf<List<LibrarySong>>(emptyList())
    private var libraryDetailAlbums by mutableStateOf<List<LibraryCollectionRow>>(emptyList())
    private var libraryDetailFolderChildren by mutableStateOf<List<LibraryCollectionRow>>(emptyList())
    private var libraryDetailGenreChildren by mutableStateOf<List<LibraryCollectionRow>>(emptyList())
    private var libraryDetailGenreAssignments by mutableStateOf<Map<String, LibraryGenreAssignmentUi>>(emptyMap())
    private var artistAlbumPreviews by mutableStateOf<Map<String, List<LibraryArtistAlbumPreview>>>(emptyMap())
    private var genreArtworkPreviews by mutableStateOf<Map<String, List<LibraryGenreArtworkPreview>>>(emptyMap())
    private var libraryBackTarget by mutableStateOf(LibraryBackTarget.DASHBOARD)
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
        playlistRepository = PlaylistRepository(runtime.database)
        metadataEditorCoordinator = MetadataEditorCoordinator(runtime.database, contentResolver)
        metadataAutonomousSettings = SharedPreferencesMetadataAutonomousSettings(this)
        metadataAutonomousEnabled = metadataAutonomousSettings.enabled
        // No verified in-app WAL backup provider exists yet; keep the switch
        // disabled and make the autonomous trigger fail closed.
        metadataAutonomousCoordinator = MetadataAutonomousApplyCoordinator(
            database = runtime.database,
            settings = metadataAutonomousSettings,
            owner = "autonomous-apply-${android.os.Process.myPid()}",
            backupVerified = { false },
        )
        executor = Executors.newSingleThreadExecutor()
        navigationExecutor = Executors.newSingleThreadExecutor()
        scanControlExecutor = Executors.newSingleThreadExecutor()
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
                    val domainOrder = queueController.orderedForPlayback()
                    val hasFollowingDomainItem = domainOrder.indexOfFirst { it.entryId == entryId } < domainOrder.lastIndex
                    if (queueController.state.value.mode == QueueMode.GHOST && PlaybackWindow.needsRefresh(activeGhostPlaybackWindowEntryIds, entryId, hasFollowingDomainItem)) {
                        syncQueueToSession(playWhenReady = playbackState.status == com.theemrock.smoothmedia.playback.PlaybackStatus.PLAYING, preservePosition = true)
                    }
                }
                if (entryChanged || abs(position - lastPersistedPlaybackPosition) >= 5_000L) {
                    persistQueue()
                    lastPersistedPlaybackPosition = position
                }
            },
        )
        libraryBackCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (showMetadataEditor) {
                    closeMetadataEditor()
                    return
                }
                if (showSourceManagement) {
                    openLibraryManagement()
                    return
                }
                if (showLibraryManagement) {
                    openSettings()
                    return
                }
                if (showSettings) {
                    openDashboard()
                    return
                }
                if (showAlbumDetail) {
                    navigateBackFromAlbumDetail()
                    return
                }
                if (showLibraryDetail) {
                    navigateBackFromLibraryDetail()
                    return
                }
                if (showLyrics) {
                    showLyrics = false
                    showNowPlaying = true
                    return
                }
                val type = filteredCollectionType
                if (showLibrary && type != null) {
                    returnToCollections(type)
                    return
                }
                if (showLibrary) {
                    navigateBackFromLibrary()
                    return
                }
                if (showCollections) {
                    navigateBackFromCollections()
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
        metadataRepository = RoomTrackMetadataRepository(runtime.database.trackMetadataDao())
        genreMetadataBackfill = GenreMetadataBackfill(
            metadataDao = runtime.database.trackMetadataDao(),
            genreRepository = RoomGenreRepository(runtime.database),
        )
        metadataEnrichment = TrackMetadataEnrichmentUseCase(
            trackDao = runtime.database.trackDao(),
            bindingDao = runtime.database.locatorBindingDao(),
            exclusionDao = runtime.database.catalogExclusionDao(),
            metadata = metadataRepository,
            reader = AndroidAudioMetadataReader(this),
            genreRepository = RoomGenreRepository(runtime.database),
        )
        metadataPreviewCoordinator = MetadataRefreshPreviewCoordinator(
            read = metadataEnrichment::readTrack,
            current = metadataRepository::find,
        )
        metadataRecoveryCoordinator = MetadataRecoverySessionCoordinator(
            database = runtime.database,
            engine = MetadataRecoveryEngine(
                read = metadataEnrichment::readTrack,
                current = metadataRepository::find,
            ),
            owner = "ui-recovery-${android.os.Process.myPid()}",
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
            runtime.database.safDocumentDao(),
        )
        lyricsCache = RoomLyricsCache(runtime.database.lyricsCacheDao())
        artworkResolver = AndroidArtworkResolver(
            contentResolver = contentResolver,
            candidates = libraryRepository,
            diskCache = ArtworkDiskCache(File(cacheDir, "artwork-thumbnails")),
            artworkRepository = ArtworkRepository(runtime.database),
        )
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
            pageAndTotalLoader = libraryRepository::loadCollectionsWithTotal,
        )
        setContent {
            SmoothMediaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SmoothMediaColors.Background,
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
                    ) {
                    if (showMetadataEditor) {
                        MetadataEditorScreen(
                            state = metadataEditorState,
                            affectedFileCount = metadataEditorItems.size,
                            saving = metadataEditorSaving,
                            progress = metadataEditorProgress,
                            error = metadataEditorError,
                            onChange = { metadataEditorState = it },
                            onSave = ::saveMetadataEditor,
                            onCancel = ::closeMetadataEditor,
                        )
                    } else if (showQueue) {
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
                    } else if (showLyrics && nowPlayingSong != null) {
                        LyricsScreen(
                            song = nowPlayingSong!!,
                            cachedLyrics = currentLyrics,
                            positionMs = playbackState.positionMs,
                            onSeek = playbackClient::seekTo,
                            onBack = { showLyrics = false; showNowPlaying = true },
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
                            onLyrics = {
                                nowPlayingSong?.trackId?.let { requestedTrackId ->
                                    executor.execute {
                                        val cached = lyricsCache.get(requestedTrackId)
                                        runOnUiThread {
                                            if (!isDestroyed && LyricsNavigationPolicy.canApply(requestedTrackId, nowPlayingSong?.trackId)) {
                                                currentLyrics = cached
                                                showLyrics = true
                                                showNowPlaying = false
                                            }
                                        }
                                    }
                                }
                            },
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
                    } else if (showAlbumDetail && albumDetailData != null) {
                        LibraryAlbumDetailScreen(
                            data = albumDetailData!!,
                            onMenu = {},
                            onSearch = { showSearch = true },
                            onDashboard = ::openDashboard,
                            onSettings = ::openSettings,
                            onPlayAlbum = {
                                albumDetailData?.songs?.firstOrNull()?.let { first ->
                                    playSong(first, albumDetailData!!.songs)
                                }
                            },
                            onEditSongs = { songs -> openMetadataEditor(songs) },
                            onSong = { song -> playSong(song, albumDetailData!!.songs) },
                            onAddToQueue = ::addToQueue,
                            onPlayNext = ::playNextSong,
                            playbackState = playbackState.takeIf { nowPlayingSong != null },
                            miniPlayerSong = nowPlayingSong,
                            onMiniPlayerOpen = { showNowPlaying = true },
                            onMiniPlayerPause = playbackClient::pause,
                            onMiniPlayerResume = playbackClient::resume,
                            onMiniPlayerNext = ::playNext,
                            artworkResolver = artworkResolver,
                        )
                    } else if (showLibraryDetail && libraryDetailType != null && libraryDetailRow != null) {
                        LibraryDetailScreen(
                            type = libraryDetailType!!,
                            row = libraryDetailRow!!,
                            songs = libraryDetailSongs,
                            artistAlbums = libraryDetailAlbums,
                            folderChildren = libraryDetailFolderChildren,
                            genreChildren = libraryDetailGenreChildren,
                            genreAssignments = libraryDetailGenreAssignments,
                            onMenu = {},
                            onSearch = { showSearch = true },
                            onDashboard = ::openDashboard,
                            onSettings = ::openSettings,
                            onAlbumSelected = ::openAlbumDetail,
                            onArtistSelected = { artist -> openLibraryDetail(LibraryCollectionType.ARTISTS, artist) },
                            onGenreSelected = { genre -> openLibraryDetail(LibraryCollectionType.GENRES, genre) },
                            onFolderSelected = { folder -> openLibraryDetail(LibraryCollectionType.FOLDERS, folder) },
                            onSong = { song -> playSong(song, libraryDetailSongs) },
                            onEditSongs = { openMetadataEditor(libraryDetailSongs) },
                            onAddToQueue = ::addToQueue,
                            onPlayNext = ::playNextSong,
                            playbackState = playbackState.takeIf { nowPlayingSong != null },
                            miniPlayerSong = nowPlayingSong,
                            onMiniPlayerOpen = { showNowPlaying = true },
                            onMiniPlayerPause = playbackClient::pause,
                            onMiniPlayerResume = playbackClient::resume,
                            onMiniPlayerNext = ::playNext,
                            artworkResolver = artworkResolver,
                        )
                    } else if (showCollections) {
                        LibraryCollectionScreen(
                            state = collectionViewModel.state.value,
                            onNearEnd = {
                                val requestedType = collectionViewModel.state.value.type
                                if (collectionPageLoadInFlight.compareAndSet(false, true)) {
                                    navigationExecutor.execute {
                                        try {
                                            if (collectionViewModel.state.value.type == requestedType) {
                                                val previousCount = collectionViewModel.state.value.collections.size
                                                collectionViewModel.loadNextPage()
                                                val newRows = collectionViewModel.state.value.collections.drop(previousCount)
                                                if (collectionViewModel.state.value.type == requestedType) {
                                                    if (requestedType == LibraryCollectionType.ARTISTS) {
                                                        refreshArtistAlbumPreviews(newRows)
                                                    } else if (requestedType == LibraryCollectionType.GENRES) {
                                                        refreshGenreArtworkPreviews(newRows)
                                                    }
                                                }
                                            }
                                        } finally {
                                            collectionPageLoadInFlight.set(false)
                                        }
                                    }
                                }
                            },
                            onRetry = { navigationExecutor.execute { collectionViewModel.retry() } },
                            onSongs = {
                                showAllSongs()
                            },
                            onCollection = ::openCollection,
                            onCollectionSelected = { row ->
                                if (collectionViewModel.state.value.type == LibraryCollectionType.ALBUMS) openAlbumDetail(row)
                                else openLibraryDetail(collectionViewModel.state.value.type, row)
                            },
                            onEditSelectedCollections = ::openMetadataEditorForCollections,
                            onSources = {
                                openSources()
                            },
                            onSearch = { showSearch = true },
                            onMenu = {},
                            onDashboard = ::openDashboard,
                            onSettings = ::openSettings,
                            artistAlbumPreviews = artistAlbumPreviews,
                            genreArtworkPreviews = genreArtworkPreviews,
                            artworkResolver = artworkResolver,
                        )
                    } else if (showDashboard) {
                        val data = dashboardData
                        if (data == null) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = androidx.compose.ui.Alignment.Center,
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(color = Color(0xFF19C3E6))
                            }
                        } else {
                            LibraryDashboardScreen(
                                data = data,
                                onSongs = ::showAllSongs,
                                onCollection = ::openCollection,
                                onCollectionSelected = { type, row ->
                                    if (type == LibraryCollectionType.ALBUMS) openAlbumDetail(row) else openLibraryDetail(type, row)
                                },
                                onSearch = { showSearch = true },
                                onSources = ::openSources,
                                onSettings = ::openSettings,
                                onSong = { song -> playSong(song) },
                                onAddToQueue = ::addToQueue,
                                onPlayNext = ::playNextSong,
                                playbackState = playbackState.takeIf { nowPlayingSong != null },
                                miniPlayerSong = nowPlayingSong,
                                onMiniPlayerOpen = { showNowPlaying = true },
                                onMiniPlayerPause = playbackClient::pause,
                                onMiniPlayerResume = playbackClient::resume,
                                onMiniPlayerNext = ::playNext,
                                artworkResolver = artworkResolver,
                            )
                        }
                    } else if (showLibrary) {
                        LibraryScreen(
                            state = libraryViewModel.state.value,
                            onNearEnd = { executor.execute { libraryViewModel.loadNextPage() } },
                            onRetry = { executor.execute { libraryViewModel.retry() } },
                            onSources = {
                                openSources()
                            },
                            onSongs = ::showAllSongs,
                            onCollection = ::openCollection,
                            onSearch = { showSearch = true },
                            onSong = { song -> playSong(song) },
                            onEditSong = { song -> openMetadataEditor(listOf(song)) },
                            onEditSelectedSongs = ::openMetadataEditor,
                            onAddSelectedToPlaylist = ::openPlaylistPicker,
                            onAddSelectedToQueue = ::addSelectedSongsToQueue,
                            playbackState = playbackState.takeIf { nowPlayingSong != null },
                            miniPlayerSong = nowPlayingSong,
                            onMiniPlayerOpen = { showNowPlaying = true },
                            onMiniPlayerPause = playbackClient::pause,
                            onMiniPlayerResume = playbackClient::resume,
                            onMiniPlayerNext = ::playNext,
                            onAddToQueue = ::addToQueue,
                            onPlayNext = ::playNextSong,
                            onMenu = {},
                            onDashboard = ::openDashboard,
                            onSettings = ::openSettings,
                            artworkResolver = artworkResolver,
                            initialScrollIndex = libraryViewModel.scrollIndex,
                            initialScrollOffset = libraryViewModel.scrollOffset,
                            onScrollPositionChanged = libraryViewModel::saveScrollPosition,
                        )
                    } else if (showSourceManagement) {
                        SourceManagementScreen(
                            sources = sources,
                            catalogTrackCounts = sourceTrackCounts,
                            statusMessage = listOfNotNull(statusMessage, scanMessage).joinToString("\n").ifBlank { null },
                            scanningSourceId = scanningSourceId,
                            scanPhase = scanPhase,
                            scanProgress = scanProgress,
                            scanExecutionAttached = scanExecutionAttached,
                            onAdd = { treePicker.launch(TreePickerIntentFactory.create()) },
                            onRefresh = { refreshSources() },
                            onRemove = { sourceId -> removeSource(sourceId) },
                            onScan = { sourceId -> startScan(sourceId) },
                            onPauseScan = ::pauseScan,
                            onResumeScan = ::resumeScan,
                            onStopScan = ::stopScan,
                            onLibrary = {
                                showAllSongs()
                            },
                            onMenu = ::openSettings,
                            onHome = ::openDashboard,
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
                            metadataPreviewRunning = metadataPreviewRunning,
                            metadataPreview = metadataPreview,
                            onMetadataPreview = ::startMetadataPreview,
                            metadataRecoveryStatus = metadataRecoveryStatus,
                            metadataRecoveryProgress = metadataRecoveryProgress,
                            onMetadataRecoveryStart = ::startMetadataRecovery,
                            onMetadataRecoveryPause = ::pauseMetadataRecovery,
                            onMetadataRecoveryResume = ::resumeMetadataRecovery,
                            onMetadataRecoveryStop = ::stopMetadataRecovery,
                            metadataApplyDryRun = metadataApplyDryRun,
                            metadataApplyDryRunRunning = metadataApplyDryRunRunning,
                            onMetadataApplyDryRun = ::startMetadataApplyDryRun,
                            metadataApplyStatus = metadataApplyStatus,
                            metadataApplyRunning = metadataApplyRunning,
                            onMetadataApply = ::startApprovedMetadataApply,
                            genreBackfillRunning = genreBackfillRunning,
                            genreBackfillStatus = genreBackfillStatus,
                            onGenreBackfill = ::startGenreBackfill,
                            autonomousApplyEnabled = metadataAutonomousEnabled,
                            autonomousApplyToggleEnabled = metadataAutonomousToggleEnabled,
                            autonomousApplyStatus = metadataAutonomousStatus,
                            onAutonomousApplyToggle = ::setMetadataAutonomousEnabledFromUi,
                            showStandaloneMetadataControls = true,
                        )
                    } else if (showLibraryManagement) {
                        LibraryManagementScreen(
                            onMenu = ::openSettings,
                            onHome = ::openDashboard,
                            onMusicSources = ::openSources,
                        )
                    } else if (showSettings) {
                        SettingsScreen(
                            onMenu = ::openSettings,
                            onHome = ::openDashboard,
                            onLibraryManagement = ::openLibraryManagement,
                        )
                    }
                    if (showPlaylistPicker) {
                        PlaylistPickerDialog(
                            playlists = playlistOptions,
                            onDismiss = { showPlaylistPicker = false },
                            onSelect = ::addSelectedSongsToPlaylist,
                            onCreate = ::createPlaylistAndAddSelectedSongs,
                        )
                    }
                    }
                }
            }
        }
        loadAndRefreshSources()
        loadMetadataRecoveryState()
        loadMetadataMissingCount()
        loadDashboard()
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
        if (::navigationExecutor.isInitialized) navigationExecutor.shutdownNow()
        if (::scanControlExecutor.isInitialized) scanControlExecutor.shutdown()
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

    override fun onResume() {
        super.onResume()
        refreshSourceTrackCounts()
    }

    private fun addToQueue(song: LibrarySong) = enqueueSong(song, playNext = false)

    private fun playNextSong(song: LibrarySong) = enqueueSong(song, playNext = true)

    private fun openPlaylistPicker(songs: List<LibrarySong>) {
        if (songs.isEmpty()) return
        playlistPickerSongs = songs.distinctBy { it.trackId }
        executor.execute {
            val options = runCatching { playlistRepository.listAll() }.getOrDefault(emptyList())
            runOnUiThread {
                playlistOptions = options
                showPlaylistPicker = true
            }
        }
    }

    private fun addSelectedSongsToPlaylist(playlist: PlaylistEntity) {
        val songs = playlistPickerSongs
        executor.execute {
            val result = runCatching { playlistRepository.addTracks(playlist.playlistId, songs.map { it.trackId }) }
            runOnUiThread {
                showPlaylistPicker = false
                statusMessage = result.fold(
                    onSuccess = { "Playlist: ${it.added} eklendi, ${it.skippedExisting + it.skippedMissing} atlandı" },
                    onFailure = { "Playlist ekleme başarısız: ${it.message ?: "bilinmeyen hata"}" },
                )
            }
        }
    }

    private fun createPlaylistAndAddSelectedSongs(name: String) {
        val songs = playlistPickerSongs
        executor.execute {
            val result = runCatching {
                val playlist = playlistRepository.create(name)
                playlistRepository.addTracks(playlist.playlistId, songs.map { it.trackId })
            }
            runOnUiThread {
                showPlaylistPicker = false
                statusMessage = result.fold(
                    onSuccess = { "Playlist oluşturuldu: ${it.added} eklendi, ${it.skippedExisting + it.skippedMissing} atlandı" },
                    onFailure = { "Playlist oluşturma başarısız: ${it.message ?: "bilinmeyen hata"}" },
                )
            }
        }
    }

    private fun addSelectedSongsToQueue(songs: List<LibrarySong>) {
        val orderedSongs = orderedUniqueTrackIds(songs.map { it.trackId }).mapNotNull { id -> songs.firstOrNull { it.trackId == id } }
        if (orderedSongs.isEmpty()) return
        executor.execute {
            val resolver = CatalogPlaybackLocationResolver(runtime.database)
            val resolved = orderedSongs.mapNotNull { song ->
                if (!song.isAccessible) return@mapNotNull null
                val location = runCatching { resolver.resolve(song.trackId).firstOrNull() }.getOrNull() ?: return@mapNotNull null
                QueueItem(song.trackId, title = song.title, artist = song.artist, album = song.album, durationMs = song.durationMs) to location
            }
            val skipped = orderedSongs.size - resolved.size
            runOnUiThread {
                var added = 0
                resolved.forEach { (item, _) ->
                    val changed = if (queueController.state.value.mode == QueueMode.GHOST) {
                        queueController.takeOverUserQueue(item, playNext = false)
                    } else queueController.add(item)
                    if (changed) added++
                }
                if (added > 0) {
                    persistQueue()
                    syncQueueToSession(
                        playWhenReady = playbackState.status == com.theemrock.smoothmedia.playback.PlaybackStatus.PLAYING,
                        preservePosition = true,
                    )
                }
                statusMessage = "Kuyruk: $added eklendi, ${skipped + (resolved.size - added)} atlandı"
            }
        }
    }

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
        val isGhostContext = queueController.state.value.mode == QueueMode.GHOST
        val window = currentEntryId?.takeIf { isGhostContext }?.let { PlaybackWindow.select(items, it) }
        val itemsForSession = window?.items ?: items
        val startIndex = window?.selectedIndex ?: itemsForSession.indexOfFirst { it.entryId == currentEntryId }.coerceAtLeast(0)
        executor.execute {
            val resolver = CatalogPlaybackLocationResolver(runtime.database)
            val resolution = if (isGhostContext && currentEntryId != null) {
                PlaybackWindowResolver.resolve(items, currentEntryId) { item -> resolver.resolve(item.trackId).firstOrNull() }
            } else null
            val resolvedItems = resolution?.items ?: itemsForSession
            val locations = resolution?.locations
                ?: resolvedItems.mapNotNull { item -> resolver.resolve(item.trackId).firstOrNull()?.let { item.entryId to it } }.toMap()
            val resolvedStartIndex = resolution?.startIndex ?: startIndex
            runOnUiThread {
                activeGhostPlaybackWindowEntryIds = if (isGhostContext) resolvedItems.map { it.entryId } else emptyList()
                playbackClient.loadQueue(resolvedItems, locations, startIndex = resolvedStartIndex, startPositionMs = if (preservePosition) playbackState.positionMs else 0L, playWhenReady = playWhenReady)
            }
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
        scanSessionId = null
        scanProgress = null
        scanExecutionAttached = true
        scanPhase = ScanPhase.STARTING
        scanMessage = getString(R.string.scan_starting)
        executor.execute {
            val source = runCatching { runtime.repository.find(sourceId) }.getOrNull()
            val result = if (source == null) {
                ManualScanResult.Rejected("", ScanSessionOrchestrator.RejectionReason.SOURCE_NOT_SELECTED)
            } else {
                scanController.start(source, ::postScanProgress) { sessionId ->
                    runOnUiThread {
                        if (!isDestroyed) scanSessionId = sessionId
                    }
                }
            }
            runOnUiThread {
                if (result !is ManualScanResult.Paused) scanningSourceId = null
                scanExecutionAttached = false
                scanSessionId = when (result) {
                    is ManualScanResult.Paused -> scanSessionId
                    else -> null
                }
                scanPhase = when (result) {
                    is ManualScanResult.Completed -> ScanPhase.COMPLETED
                    is ManualScanResult.Paused -> ScanPhase.PAUSED
                    is ManualScanResult.Cancelled -> ScanPhase.CANCELLED
                    else -> ScanPhase.FAILED
                }
                scanMessage = scanResultMessage(result)
            }
            refreshSourceTrackCounts()
        }
    }

    private fun pauseScan() {
        val sessionId = scanSessionId ?: return
        scanPhase = ScanPhase.PAUSING
        scanControlExecutor.execute {
            val applied = scanController.pause(sessionId)
            runOnUiThread {
                if (!isDestroyed && !applied) {
                    scanPhase = ScanPhase.PROCESSING
                    scanMessage = "Tarama duraklatılamadı; oturum durumu değişti."
                }
            }
        }
    }

    private fun stopScan() {
        val sessionId = scanSessionId ?: return
        scanPhase = ScanPhase.STOPPING
        scanControlExecutor.execute {
            val applied = scanController.cancel(sessionId)
            runOnUiThread {
                if (!isDestroyed && !applied) {
                    scanPhase = ScanPhase.PROCESSING
                    scanMessage = "Tarama durdurulamadı; oturum durumu değişti."
                }
            }
        }
    }

    private fun resumeScan() {
        val sessionId = scanSessionId ?: return
        val sourceId = scanningSourceId ?: return
        scanPhase = ScanPhase.RESUMING
        scanExecutionAttached = true
        executor.execute {
            val source = runCatching { runtime.repository.find(sourceId) }.getOrNull()
            val result = if (source == null) {
                ManualScanResult.Rejected(sessionId, ScanSessionOrchestrator.RejectionReason.SOURCE_NOT_SELECTED)
            } else {
                scanController.resume(sessionId, source, ::postScanProgress)
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (result !is ManualScanResult.Paused) {
                    scanningSourceId = null
                    scanSessionId = null
                }
                scanExecutionAttached = result is ManualScanResult.Paused
                scanPhase = when (result) {
                    is ManualScanResult.Completed -> ScanPhase.COMPLETED
                    is ManualScanResult.Paused -> ScanPhase.PAUSED
                    is ManualScanResult.Cancelled -> ScanPhase.CANCELLED
                    else -> ScanPhase.FAILED
                }
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

    private fun startMetadataRecovery() {
        executor.execute {
            val sessionId = runCatching { metadataRecoveryCoordinator.start() }.getOrNull() ?: return@execute
            metadataRecoverySessionId = sessionId
            runMetadataRecoveryLoop(sessionId)
        }
    }

    private fun loadMetadataRecoveryState() {
        executor.execute {
            val session = runCatching { runtime.database.metadataRecoveryDao().findLatestSession() }.getOrNull() ?: return@execute
            runOnUiThread {
                metadataRecoverySessionId = session.sessionId
                metadataRecoveryStatus = session.status
                metadataRecoveryProgress = session
            }
            if (session.status == MetadataRecoverySessionStatus.COMPLETED) {
                triggerAutonomousApply(session.sessionId)
            }
        }
    }

    private fun runMetadataRecoveryLoop(sessionId: String) {
        while (!isDestroyed) {
            val attempt = runCatching { metadataRecoveryCoordinator.runNextBatch(sessionId) }
            val status = attempt.getOrElse {
                metadataRecoveryCoordinator.fail(sessionId)
                MetadataRecoverySessionStatus.FAILED
            }
            val snapshot = runtime.database.metadataRecoveryDao().findSession(sessionId)
            runOnUiThread {
                metadataRecoveryStatus = status
                metadataRecoveryProgress = snapshot
            }
            if (status == MetadataRecoverySessionStatus.COMPLETED) {
                triggerAutonomousApply(sessionId)
            }
            if (status != MetadataRecoverySessionStatus.RUNNING) return
            if (snapshot?.leaseOwner != null) Thread.sleep(250)
        }
    }

    private fun pauseMetadataRecovery() {
        val sessionId = metadataRecoverySessionId ?: return
        scanControlExecutor.execute { metadataRecoveryCoordinator.pause(sessionId) }
    }

    private fun resumeMetadataRecovery() {
        val sessionId = metadataRecoverySessionId ?: return
        scanControlExecutor.execute {
            if (metadataRecoveryCoordinator.resume(sessionId)) runMetadataRecoveryLoop(sessionId)
        }
    }

    private fun stopMetadataRecovery() {
        val sessionId = metadataRecoverySessionId ?: return
        scanControlExecutor.execute {
            metadataRecoveryCoordinator.cancel(sessionId)
            val snapshot = runtime.database.metadataRecoveryDao().findSession(sessionId)
            runOnUiThread {
                metadataRecoveryStatus = MetadataRecoverySessionStatus.CANCELLED
                metadataRecoveryProgress = snapshot
            }
        }
    }

    private fun setMetadataAutonomousEnabledFromUi(enabled: Boolean) {
        if (enabled && !metadataAutonomousToggleEnabled) {
            metadataAutonomousStatus = "Otonom onarım etkinleştirilemedi: doğrulanmış WAL yedeği gerekli."
            return
        }
        metadataAutonomousSettings.setEnabled(enabled)
        metadataAutonomousEnabled = enabled
        metadataAutonomousStatus = if (enabled) "Otonom onarım etkin." else "Otonom onarım devre dışı."
    }

    private fun triggerAutonomousApply(auditSessionId: String) {
        if (!metadataAutonomousEnabled) {
            metadataAutonomousStatus = "Audit tamamlandı; otonom onarım devre dışı."
            return
        }
        if (!metadataAutonomousTriggerRunning.compareAndSet(false, true)) return
        executor.execute {
            try {
                val result = metadataAutonomousCoordinator.onAuditCompleted(auditSessionId)
                runOnUiThread {
                    metadataAutonomousStatus = when (result.decision) {
                        MetadataAutonomousApplyDecision.DISABLED -> "Otonom onarım devre dışı."
                        MetadataAutonomousApplyDecision.BLOCKED_AUDIT -> "Otonom onarım engellendi: audit tamamlanmadı."
                        MetadataAutonomousApplyDecision.BLOCKED_BACKUP -> "Otonom onarım engellendi: doğrulanmış yedek yok."
                        MetadataAutonomousApplyDecision.NO_WORK -> "Otonom onarım için yeni uygun karar yok."
                        MetadataAutonomousApplyDecision.READY -> "Otonom onarım sonucu: ${result.succeeded} alan uygulandı."
                    }
                }
            } finally {
                metadataAutonomousTriggerRunning.set(false)
            }
        }
    }

    private fun startMetadataApplyDryRun() {
        if (metadataApplyDryRunRunning) return
        metadataApplyDryRunRunning = true
        executor.execute {
            val result = runCatching {
                val audit = runtime.database.metadataRecoveryDao().findLatestCompletedSession()
                    ?: error("Tamamlanmış audit session bulunamadı")
                RoomMetadataApplyDryRun(runtime.database).evaluate(audit.sessionId)
            }.getOrNull()
            runOnUiThread {
                metadataApplyDryRun = result
                metadataApplyDryRunRunning = false
            }
        }
    }

    private fun startApprovedMetadataApply() {
        if (metadataApplyRunning) return
        metadataApplyRunning = true
        executor.execute {
            val status = runCatching {
                val audit = runtime.database.metadataRecoveryDao().findLatestCompletedSession()
                    ?: error("Tamamlanmış audit session bulunamadı")
                val dryRun = RoomMetadataApplyDryRun(runtime.database)
                val inputs = dryRun.approvedInputs(audit.sessionId)
                check(inputs.size == 59) { "Onaylı apply kapsamı 59 alan değil" }
                val store = RoomMetadataApplySessionStore(runtime.database)
                val applySessionId = store.prepareDryRun(audit.sessionId, inputs)
                val applyExecutor = RoomMetadataApplyExecutor(
                    database = runtime.database,
                    writer = ControlledMetadataRoomWriter(runtime.database),
                    owner = "ui-approved-apply-${android.os.Process.myPid()}",
                )
                var result = applyExecutor.runNextBatch(applySessionId, applyEnabled = true, backupVerified = true)
                while (result.status == com.theemrock.smoothmedia.metadata.MetadataApplyExecutionStatus.RUNNING) {
                    result = applyExecutor.runNextBatch(applySessionId, applyEnabled = true, backupVerified = true)
                }
                check(result.status == com.theemrock.smoothmedia.metadata.MetadataApplyExecutionStatus.COMPLETED) {
                    "Metadata apply tamamlanmadı: ${result.status}"
                }
                "Apply tamamlandı: 59/59 alan"
            }.getOrElse { "Apply başarısız: ${it.message ?: "bilinmeyen hata"}" }
            runOnUiThread {
                metadataApplyStatus = status
                metadataApplyRunning = false
            }
        }
    }

    private fun startMetadataPreview() {
        if (metadataPreviewRunning) return
        metadataPreviewRunning = true
        executor.execute {
            val result = runCatching {
                val dao = runtime.database.trackMetadataDao()
                val suspicious = dao.listActiveTrackIdsWithMojibakeMarkers(MetadataRefreshPreviewCoordinator.MAX_ITEMS)
                val ids = (suspicious + dao.listActiveTrackIds(MetadataRefreshPreviewCoordinator.MAX_ITEMS))
                    .distinct()
                    .take(MetadataRefreshPreviewCoordinator.MAX_ITEMS)
                metadataPreviewCoordinator.preview(ids)
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                metadataPreviewRunning = false
                result.onSuccess { metadataPreview = it }
                    .onFailure { statusMessage = "Metadata önizlemesi başarısız: ${it.message ?: "okuma hatası"}" }
            }
        }
    }

    private fun navigateBackFromLibrary() {
        val type = filteredCollectionType
        if (type != null) {
            returnToCollections(type)
            return
        }
        when (libraryBackTarget) {
            LibraryBackTarget.COLLECTIONS -> {
                showLibrary = false
                showCollections = true
                showDashboard = false
                updateLibraryBackCallback()
            }
            LibraryBackTarget.DASHBOARD,
            LibraryBackTarget.LIBRARY,
            LibraryBackTarget.DETAIL
            -> openDashboard()
        }
    }

    private fun navigateBackFromAlbumDetail() {
        showAlbumDetail = false
        albumDetailData = null
        if (libraryBackTarget == LibraryBackTarget.DETAIL) {
            showDashboard = false
            showLibrary = false
            showCollections = false
            showLibraryDetail = true
        } else if (libraryBackTarget == LibraryBackTarget.COLLECTIONS) {
            showDashboard = false
            showLibrary = false
            showCollections = true
        } else {
            openDashboard()
            return
        }
        updateLibraryBackCallback()
    }

    private fun navigateBackFromLibraryDetail() {
        showLibraryDetail = false
        libraryDetailType = null
        libraryDetailRow = null
        libraryDetailSongs = emptyList()
        if (libraryBackTarget == LibraryBackTarget.COLLECTIONS) {
            showDashboard = false
            showLibrary = false
            showCollections = true
        } else {
            openDashboard()
            return
        }
        updateLibraryBackCallback()
    }

    private fun navigateBackFromCollections() {
        when (libraryBackTarget) {
            LibraryBackTarget.LIBRARY -> showAllSongs()
            LibraryBackTarget.DASHBOARD,
            LibraryBackTarget.COLLECTIONS,
            LibraryBackTarget.DETAIL
            -> openDashboard()
        }
    }

    private fun openCollection(type: LibraryCollectionType) {
        libraryBackTarget = when {
            showLibrary -> LibraryBackTarget.LIBRARY
            showCollections -> LibraryBackTarget.COLLECTIONS
            else -> LibraryBackTarget.DASHBOARD
        }
        showDashboard = false
        showCollections = true
        showLibrary = false
        filteredCollectionType = null
        artistAlbumPreviews = emptyMap()
        genreArtworkPreviews = emptyMap()
        updateLibraryBackCallback()
        submitLatestNavigation { _ ->
            collectionViewModel.loadInitial(type)
            if (type == LibraryCollectionType.ARTISTS) refreshArtistAlbumPreviews()
            if (type == LibraryCollectionType.GENRES) refreshGenreArtworkPreviews()
        }
    }

    private fun refreshArtistAlbumPreviews(rows: List<LibraryCollectionRow> = collectionViewModel.state.value.collections) {
        val missingRows = rows.filterNot { it.title in artistAlbumPreviews }
        if (missingRows.isEmpty()) return
        val previewRows = libraryRepository.loadArtistAlbumPreviews(missingRows.map { it.title })
        val previews = missingRows.associate { row -> row.title to previewRows[row.title].orEmpty() }
        runOnUiThread {
            if (!isDestroyed) artistAlbumPreviews = artistAlbumPreviews + previews
        }
    }

    private fun refreshGenreArtworkPreviews(rows: List<LibraryCollectionRow> = collectionViewModel.state.value.collections) {
        val missingRows = rows.filterNot { it.title in genreArtworkPreviews }
        if (missingRows.isEmpty()) return
        val previews = missingRows.associate { row ->
            row.title to libraryRepository.loadGenreArtworkPreviews(row.title)
        }
        runOnUiThread {
            if (!isDestroyed) genreArtworkPreviews = genreArtworkPreviews + previews
        }
    }

    private fun submitLatestNavigation(block: (Long) -> Unit) {
        val request = navigationGeneration.incrementAndGet()
        navigationExecutor.execute {
            if (isLatestNavigation(request)) block(request)
        }
    }

    private fun isLatestNavigation(request: Long): Boolean = navigationGeneration.get() == request

    private fun openMetadataEditor(songs: List<LibrarySong>) {
        val ids = songs.map { it.trackId }.distinct()
        if (ids.isEmpty()) return
        val generation = metadataEditorGeneration.incrementAndGet()
        metadataEditorState = MetadataEditorState(ids)
        metadataEditorItems = emptyList()
        metadataEditorError = null
        metadataEditorProgress = "Dosya hedefleri hazırlanıyor..."
        metadataEditorCancelRequested.set(false)
        showMetadataEditor = true
        executor.execute {
            val loaded = runCatching { metadataEditorCoordinator.load(ids) }
            runOnUiThread {
                if (isDestroyed || generation != metadataEditorGeneration.get()) return@runOnUiThread
                loaded.onSuccess { result ->
                    metadataEditorItems = result.items
                    if (!metadataEditorState.isDirty) {
                        val snapshots = result.items
                            .distinctBy { it.target?.trackId ?: it.id }
                            .map { it.snapshot }
                        if (ids.size == 1) {
                            snapshots.firstOrNull()?.let { snapshot ->
                                metadataEditorState = metadataEditorState.prefill(snapshot)
                            }
                        } else if (snapshots.isNotEmpty()) {
                            metadataEditorState = metadataEditorState.prefill(snapshots)
                        }
                    }
                    metadataEditorProgress = if (result.skippedTrackIds.isEmpty()) null else
                        "${result.skippedTrackIds.size} kayıt için erişilebilir MP3/FLAC location bulunamadı."
                }.onFailure { error ->
                    metadataEditorError = error.message ?: "Metadata hedefleri okunamadı."
                    metadataEditorProgress = null
                }
            }
        }
    }

    private fun openMetadataEditorForCollections(
        type: LibraryCollectionType,
        rows: List<LibraryCollectionRow>,
    ) {
        if (rows.isEmpty() || (type != LibraryCollectionType.ALBUMS && type != LibraryCollectionType.ARTISTS)) return
        submitLatestNavigation { request ->
            val songPages = mutableListOf<List<LibrarySong>>()
            rows.forEach { row ->
                val filter = if (type == LibraryCollectionType.ALBUMS) {
                    AlbumGroupingKey.decode(row.key, row.title, row.subtitle.orEmpty()).toFilter()
                } else {
                    LibraryTrackFilter.Artist(row.title)
                }
                var offset = 0
                do {
                    val page = libraryRepository.loadFilteredPage(filter, offset, 200)
                    songPages += page.songs
                    offset += page.songs.size
                } while (page.hasMore && page.songs.isNotEmpty())
            }
            runOnUiThread {
                if (!isDestroyed && isLatestNavigation(request)) openMetadataEditor(deduplicateLibrarySongs(songPages))
            }
        }
    }

    private fun saveMetadataEditor() {
        if (metadataEditorSaving || metadataEditorItems.isEmpty()) return
        metadataEditorSaving = true
        metadataEditorError = null
        metadataEditorProgress = "Yazılıyor..."
        metadataEditorCancelRequested.set(false)
        val edit = metadataEditorState.toFileTagEdit()
        executor.execute {
            val result = runCatching {
                metadataEditorCoordinator.save(
                    items = metadataEditorItems,
                    edit = edit,
                    cancelled = metadataEditorCancelRequested,
                ) { completed, total ->
                    runOnUiThread { if (!isDestroyed) metadataEditorProgress = "$completed/$total dosya doğrulandı" }
                }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                metadataEditorSaving = false
                result.onSuccess { summary ->
                    if (summary.blocked) {
                        metadataEditorProgress = "Fiziksel yazma kilitli; dosya ve Room deÄŸiÅŸmedi."
                    } else {
                    metadataEditorProgress = "Tamamlandı: ${summary.completed} başarılı, ${summary.failed} hata" + if (summary.cancelled) ", iptal edildi" else ""
                    }
                }.onFailure { error ->
                    metadataEditorError = error.message ?: "Metadata yazımı başarısız."
                    metadataEditorProgress = null
                }
            }
        }
    }

    private fun closeMetadataEditor() {
        metadataEditorGeneration.incrementAndGet()
        if (metadataEditorSaving) {
            metadataEditorCancelRequested.set(true)
            metadataEditorProgress = "İptal bekleniyor..."
        } else {
            showMetadataEditor = false
            metadataEditorProgress = null
            metadataEditorError = null
        }
    }

    private fun openAlbumDetail(row: LibraryCollectionRow) {
        libraryBackTarget = when {
            showLibraryDetail -> LibraryBackTarget.DETAIL
            showCollections -> LibraryBackTarget.COLLECTIONS
            else -> LibraryBackTarget.DASHBOARD
        }
        showDashboard = false
        showLibrary = false
        showCollections = false
        showAlbumDetail = true
        showLibraryDetail = false
        filteredCollectionType = null
        albumDetailData = null
        updateLibraryBackCallback()
        submitLatestNavigation { request ->
            val filter = AlbumGroupingKey.decode(row.key, row.title, row.subtitle.orEmpty()).toFilter()
            val songs = mutableListOf<LibrarySong>()
            var offset = 0
            do {
                val page = libraryRepository.loadFilteredPage(filter, offset, 200)
                songs += page.songs
                offset += page.songs.size
            } while (page.hasMore && page.songs.isNotEmpty())
            val detail = LibraryAlbumDetailData(row, songs)
            runOnUiThread { if (!isDestroyed && isLatestNavigation(request)) albumDetailData = detail }
        }
    }

    private fun openLibraryDetail(type: LibraryCollectionType, row: LibraryCollectionRow) {
        libraryBackTarget = if (showCollections) LibraryBackTarget.COLLECTIONS else LibraryBackTarget.DASHBOARD
        showDashboard = false
        showLibrary = false
        showCollections = false
        showAlbumDetail = false
        showLibraryDetail = true
        libraryDetailType = type
        libraryDetailRow = row
        libraryDetailSongs = emptyList()
        libraryDetailAlbums = emptyList()
        libraryDetailFolderChildren = emptyList()
        libraryDetailGenreChildren = emptyList()
        libraryDetailGenreAssignments = emptyMap()
        updateLibraryBackCallback()
        submitLatestNavigation { request ->
            val folderParts = row.key.split('\u001E')
            val filter = when (type) {
                LibraryCollectionType.ARTISTS -> LibraryTrackFilter.Artist(row.title)
                LibraryCollectionType.GENRES -> LibraryTrackFilter.GenreId(row.key, includeDescendants = true)
                LibraryCollectionType.FOLDERS -> if (folderParts.size == 2) {
                    LibraryTrackFilter.SafFolder(folderParts[0], folderParts[1])
                } else LibraryTrackFilter.Folder(row.key)
                LibraryCollectionType.ALBUMS -> return@submitLatestNavigation
            }
            val songs = mutableListOf<LibrarySong>()
            var offset = 0
            do {
                val page = libraryRepository.loadFilteredPage(filter, offset, 200)
                songs += page.songs
                offset += page.songs.size
            } while (page.hasMore && page.songs.isNotEmpty())
            val albums = if (type == LibraryCollectionType.ARTISTS) {
                libraryRepository.loadArtistAlbumRows(row.title)
            } else {
                emptyList()
            }
            val folderChildren = if (type == LibraryCollectionType.FOLDERS && folderParts.size == 2) {
                libraryRepository.loadSafFolderChildren(folderParts[0], folderParts[1])
                    .map { child ->
                        LibraryCollectionRow(
                            folderParts[0] + '\u001E' + child.documentId,
                            child.displayName,
                            child.parentDocumentId,
                            libraryRepository.countSafFolderDirectTracks(folderParts[0], child.documentId),
                        )
                    }
            } else emptyList()
            val genreChildren = if (type == LibraryCollectionType.GENRES) {
                RoomGenreRepository(runtime.database).childGenresOf(row.key).map { child ->
                    LibraryCollectionRow(child.id, child.displayName, null, child.directTrackCount)
                }
            } else emptyList()
            val genreAssignments = if (type == LibraryCollectionType.GENRES) {
                RoomGenreRepository(runtime.database).assignmentDisplayForTracks(songs.map { it.trackId }).mapValues { (_, value) ->
                    LibraryGenreAssignmentUi(value.primary, value.secondary)
                }
            } else emptyMap()
            runOnUiThread {
                if (!isDestroyed && isLatestNavigation(request)) {
                    libraryDetailSongs = songs
                    libraryDetailAlbums = albums
                    libraryDetailFolderChildren = folderChildren
                    libraryDetailGenreChildren = genreChildren
                    libraryDetailGenreAssignments = genreAssignments
                }
            }
        }
    }

    private fun openCollectionSongs(type: LibraryCollectionType, row: LibraryCollectionRow) {
        libraryBackTarget = LibraryBackTarget.COLLECTIONS
        showDashboard = false
        showAlbumDetail = false
        val filter = when (type) {
            LibraryCollectionType.ALBUMS -> AlbumGroupingKey.decode(row.key, row.title, row.subtitle.orEmpty()).toFilter()
            LibraryCollectionType.ARTISTS -> LibraryTrackFilter.Artist(row.title)
            LibraryCollectionType.GENRES -> LibraryTrackFilter.GenreId(row.key, includeDescendants = true)
            LibraryCollectionType.FOLDERS -> row.key.split('\u001E').takeIf { it.size == 2 }
                ?.let { LibraryTrackFilter.SafFolder(it[0], it[1]) }
                ?: LibraryTrackFilter.Folder(row.key)
        }
        showCollections = false
        showLibrary = true
        showLibraryDetail = false
        filteredCollectionType = type
        updateLibraryBackCallback()
        submitLatestNavigation { _ -> libraryViewModel.loadInitial(filter, row.title) }
    }

    private fun showAllSongs() {
        if (showLibrary && filteredCollectionType == null && libraryViewModel.state.value.loaded) {
            updateLibraryBackCallback()
            return
        }
        libraryBackTarget = if (showCollections) LibraryBackTarget.COLLECTIONS else LibraryBackTarget.DASHBOARD
        showCollections = false
        showAlbumDetail = false
        showLibraryDetail = false
        showDashboard = false
        showLibrary = true
        filteredCollectionType = null
        updateLibraryBackCallback()
        submitLatestNavigation { _ -> libraryViewModel.loadInitial(null, "Your library") }
    }

    private fun returnToCollections(type: LibraryCollectionType) {
        showDashboard = false
        showCollections = true
        showAlbumDetail = false
        showLibraryDetail = false
        showLibrary = false
        filteredCollectionType = null
        updateLibraryBackCallback()
        if (collectionViewModel.state.value.type != type || !collectionViewModel.state.value.loaded) {
            submitLatestNavigation { _ -> collectionViewModel.loadInitial(type) }
        }
    }

    private fun openSources() {
        showDashboard = false
        showLibrary = false
        showCollections = false
        showAlbumDetail = false
        showLibraryDetail = false
        showSearch = false
        showSettings = false
        showLibraryManagement = false
        showSourceManagement = true
        updateLibraryBackCallback()
        loadMetadataMissingCount()
    }

    private fun openSettings() {
        showDashboard = false
        showLibrary = false
        showCollections = false
        showAlbumDetail = false
        showLibraryDetail = false
        showSearch = false
        showSourceManagement = false
        showLibraryManagement = false
        showSettings = true
        updateLibraryBackCallback()
    }

    private fun openLibraryManagement() {
        showDashboard = false
        showLibrary = false
        showCollections = false
        showAlbumDetail = false
        showLibraryDetail = false
        showSearch = false
        showSourceManagement = false
        showSettings = true
        showLibraryManagement = true
        updateLibraryBackCallback()
    }

    private fun openDashboard() {
        libraryBackTarget = LibraryBackTarget.DASHBOARD
        showDashboard = true
        showLibrary = false
        showCollections = false
        showAlbumDetail = false
        showLibraryDetail = false
        albumDetailData = null
        showSearch = false
        showSettings = false
        showLibraryManagement = false
        showSourceManagement = false
        showNowPlaying = false
        showLyrics = false
        showEqualizer = false
        showQueue = false
        updateLibraryBackCallback()
        loadDashboard()
    }

    private fun loadDashboard() {
        if (!::libraryRepository.isInitialized || !::executor.isInitialized) return
        executor.execute {
            runCatching { libraryRepository.loadDashboard() }
                .onSuccess { data -> runOnUiThread { if (!isDestroyed) dashboardData = data } }
                .onFailure { runOnUiThread { if (!isDestroyed) dashboardData = null } }
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
            val count = resolveMetadataMissingCount(metadataMissingCount) {
                runtime.database.trackMetadataDao().countTracksMissingMetadata()
            }
            runOnUiThread {
                if (!isDestroyed) metadataMissingCount = count
            }
        }
    }

    private fun startGenreBackfill() {
        if (genreBackfillRunning) return
        genreBackfillRunning = true
        genreBackfillStatus = null
        executor.execute {
            val result = runCatching { genreMetadataBackfill.run() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                genreBackfillRunning = false
                genreBackfillStatus = result.fold(
                    onSuccess = { "${it.processedTracks} track için ${it.createdAssignments} genre ilişkisi işlendi." },
                    onFailure = { "Genre verisi oluşturulamadı: ${it.message ?: "bilinmeyen hata"}" },
                )
                if (result.isSuccess) {
                    loadDashboard()
                    if (showCollections && collectionViewModel.state.value.type == LibraryCollectionType.GENRES) {
                        collectionViewModel.loadInitial(LibraryCollectionType.GENRES)
                    }
                }
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
            scanPhase = progress.phase
            scanProgress = progress
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
        loadSourceTrackCounts(updatedSources)
        loadPersistedScanState()
    }

    private fun loadSourceTrackCounts(sourceSnapshot: List<LibrarySourceEntity> = sources) {
        if (!::executor.isInitialized || !::runtime.isInitialized) return
        executor.execute {
            val counts = SourceTrackCountSnapshot.load(
                sources = sourceSnapshot,
                countTracksForSource = runtime.database.libraryDao()::countActiveTracksForSource,
            ).bySource
            runOnUiThread {
                if (!isDestroyed) sourceTrackCounts = counts
            }
        }
    }

    private fun refreshSourceTrackCounts() {
        if (!::executor.isInitialized || !::runtime.isInitialized) return
        executor.execute {
            val refreshedSources = runCatching { runtime.repository.listAll() }.getOrNull() ?: return@execute
            val counts = SourceTrackCountSnapshot.load(
                sources = refreshedSources,
                countTracksForSource = runtime.database.libraryDao()::countActiveTracksForSource,
            ).bySource
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                sources = refreshedSources
                sourceTrackCounts = counts
            }
        }
    }

    private fun loadPersistedScanState() {
        if (!::executor.isInitialized || !::runtime.isInitialized) return
        executor.execute {
            val session = runtime.database.scanDao().findLatestResumableSession()
            val checkpoint = session?.let { runtime.database.scanDao().findLatestCheckpoint(it.sessionId) }
            if (session == null || checkpoint == null) return@execute
            runOnUiThread {
                if (isDestroyed || scanningSourceId != null) return@runOnUiThread
                scanningSourceId = checkpoint.sourceId
                scanSessionId = session.sessionId
                scanExecutionAttached = false
                scanPhase = if (session.status == com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus.PAUSED) ScanPhase.PAUSED else ScanPhase.PROCESSING
                scanProgress = ManualScanProgress(
                    sessionId = session.sessionId,
                    status = session.status,
                    phase = scanPhase,
                    processedCount = checkpoint.processedCount,
                    discoveredCount = checkpoint.discoveredCount,
                    errorCount = 0,
                )
            }
        }
    }
}

@Composable
private fun SmoothMediaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
