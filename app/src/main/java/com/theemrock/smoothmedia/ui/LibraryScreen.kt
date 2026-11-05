package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.theemrock.smoothmedia.library.LibraryLocationState
import com.theemrock.smoothmedia.library.LibraryCollectionType
import com.theemrock.smoothmedia.library.LibraryMetadataState
import com.theemrock.smoothmedia.library.LibrarySong
import com.theemrock.smoothmedia.library.LibraryUiState
import com.theemrock.smoothmedia.library.LibrarySongSelection
import com.theemrock.smoothmedia.library.selectedLibrarySongs
import com.theemrock.smoothmedia.playback.PlaybackEngineState
import com.theemrock.smoothmedia.artwork.AndroidArtworkResolver
import com.theemrock.smoothmedia.artwork.ArtworkRequest
import kotlinx.coroutines.flow.distinctUntilChanged

private val Ink = Color(0xFF080B0D)
private val Line = Color(0xFF26343B)
private val Muted = Color(0xFFAAB9C2)
private val Cyan = Color(0xFF19C3E6)

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onNearEnd: () -> Unit,
    onRetry: () -> Unit = {},
    onSources: () -> Unit,
    onSongs: () -> Unit = {},
    onCollection: (LibraryCollectionType) -> Unit = {},
    onSearch: () -> Unit = {},
    onSong: (LibrarySong) -> Unit = {},
    onEditSong: (LibrarySong) -> Unit = {},
    onEditSelectedSongs: (List<LibrarySong>) -> Unit = {},
    onAddSelectedToPlaylist: (List<LibrarySong>) -> Unit = {},
    onAddSelectedToQueue: (List<LibrarySong>) -> Unit = {},
    onAddToQueue: (LibrarySong) -> Unit = {},
    onPlayNext: (LibrarySong) -> Unit = {},
    onMenu: () -> Unit = {},
    onDashboard: () -> Unit = {},
    onSettings: () -> Unit = {},
    playbackState: PlaybackEngineState? = null,
    miniPlayerSong: LibrarySong? = null,
    onMiniPlayerOpen: () -> Unit = {},
    onMiniPlayerPause: () -> Unit = {},
    onMiniPlayerResume: () -> Unit = {},
    onMiniPlayerNext: () -> Unit = {},
    artworkResolver: AndroidArtworkResolver? = null,
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollPositionChanged: (Int, Int) -> Unit = { _, _ -> },
    counts: LibraryCategoryCounts = LibraryCategoryCounts(),
) {
    Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryChrome(
                counts = counts,
                selected = LibraryChromeTab.SONGS,
                onSearch = onSearch,
                onDashboard = onDashboard,
                onSongs = onSongs,
                onCollection = onCollection,
                onSources = onSources,
                onSettings = onSettings,
            )
            val shownState = remember(state.songs, state.isFiltered) {
                if (state.isFiltered) state.songs else sortSongsByTitle(state.songs)
            }.let { state.copy(songs = it) }
            LibraryHeader(
                heading = state.heading,
                isFiltered = state.isFiltered,
                totalTracks = state.totalTracks,
                onSongs = onSongs,
                onCollection = onCollection,
            )
            when {
                state.errorMessage != null -> ErrorState(state.errorMessage, onRetry)
                !state.loaded || (state.loading && state.songs.isEmpty()) -> LoadingState()
                state.songs.isEmpty() -> EmptyState()
                else -> Box(modifier = Modifier.weight(1f)) {
                    SongsList(shownState, onNearEnd, artworkResolver, onSong, onEditSong, onAddToQueue, onPlayNext, onEditSelectedSongs, onAddSelectedToPlaylist, onAddSelectedToQueue, initialScrollIndex, initialScrollOffset, onScrollPositionChanged)
                }
            }
            if (playbackState != null && miniPlayerSong != null) {
                MiniPlayer(
                    song = miniPlayerSong,
                    state = playbackState,
                    onOpen = onMiniPlayerOpen,
                    onPause = onMiniPlayerPause,
                    onResume = onMiniPlayerResume,
                    onNext = onMiniPlayerNext,
                    artworkResolver = artworkResolver,
                )
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    heading: String,
    isFiltered: Boolean,
    totalTracks: Int,
    onSongs: () -> Unit,
    onCollection: (LibraryCollectionType) -> Unit,
) {
    if (isFiltered || (heading.isNotBlank() && heading != "Your library")) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 12.dp)) {
            if (isFiltered) Text("Filtrelenmiş şarkılar · $totalTracks şarkı", color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (heading.isNotBlank() && heading != "Your library") {
                Text(heading, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun LibrarySectionTabs(
    selected: LibraryCollectionType?,
    onSongs: () -> Unit,
    onCollection: (LibraryCollectionType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionTab("SONGS", selected == null, onSongs)
        SectionTab("ALBUMS", selected == LibraryCollectionType.ALBUMS) {
            onCollection(LibraryCollectionType.ALBUMS)
        }
        SectionTab("ARTISTS", selected == LibraryCollectionType.ARTISTS) {
            onCollection(LibraryCollectionType.ARTISTS)
        }
        SectionTab("GENRES", selected == LibraryCollectionType.GENRES) {
            onCollection(LibraryCollectionType.GENRES)
        }
        SectionTab("FOLDERS", selected == LibraryCollectionType.FOLDERS) {
            onCollection(LibraryCollectionType.FOLDERS)
        }
    }
}

@Composable
private fun RowScope.SectionTab(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .semantics { contentDescription = "Library section: $label" },
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Text(label, color = if (selected) Cyan else Muted, fontSize = 9.sp, letterSpacing = .4.sp)
    }
}

@Composable
private fun SongsList(state: LibraryUiState, onNearEnd: () -> Unit, artworkResolver: AndroidArtworkResolver?, onSong: (LibrarySong) -> Unit, onEditSong: (LibrarySong) -> Unit, onAddToQueue: (LibrarySong) -> Unit, onPlayNext: (LibrarySong) -> Unit, onEditSelectedSongs: (List<LibrarySong>) -> Unit, onAddSelectedToPlaylist: (List<LibrarySong>) -> Unit, onAddSelectedToQueue: (List<LibrarySong>) -> Unit, initialScrollIndex: Int, initialScrollOffset: Int, onScrollPositionChanged: (Int, Int) -> Unit) {
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selection by remember { mutableStateOf(LibrarySongSelection()) }
    LaunchedEffect(state.songs) {
        selection = selection.reconcile(state.songs.map { it.trackId }.toSet())
    }
    LaunchedEffect(state.heading, state.isFiltered, state.totalTracks, state.songs.firstOrNull()?.trackId) {
        selection = LibrarySongSelection()
        selectionMode = false
    }
    // Long press starts selection mode with the pressed song already selected.
    val startSelection: (LibrarySong) -> Unit = { song ->
        selectionMode = true
        if (song.trackId !in selection.trackIds) selection = selection.toggle(song.trackId)
    }
    var gridMode by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState(initialScrollIndex, initialScrollOffset)
    val gridState = rememberLazyGridState()
    val showIndex = !state.isFiltered
    val firstIndexByBucket = remember(state.songs) { AlphabetIndex.firstIndexByBucket(state.songs.map { AlphabetIndex.bucket(it.title) }) }
    var activeLetter by remember { mutableStateOf<String?>(null) }
    val indexScope = androidx.compose.runtime.rememberCoroutineScope()
    val latestNearEnd = rememberUpdatedState(onNearEnd)
    val latestState = rememberUpdatedState(state)
    val latestScrollCallback = rememberUpdatedState(onScrollPositionChanged)
    DisposableEffect(listState) {
        onDispose {
            latestScrollCallback.value(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                val current = latestState.value
                if (!current.loading && current.errorMessage == null && current.hasMore && last >= current.songs.lastIndex - 5) {
                    latestNearEnd.value()
                }
            }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selectionMode) {
                val selectedSongs = selectedLibrarySongs(state.songs, selection.trackIds)
                // Count stays fixed on the left; only the actions scroll.
                Text("${selection.trackIds.size} seçildi", color = Muted, fontSize = 13.sp)
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(enabled = selection.trackIds.isNotEmpty(), onClick = { onEditSelectedSongs(selectedSongs) }) { Text("Düzenle") }
                    OutlinedButton(enabled = selectedSongs.isNotEmpty(), onClick = { onAddSelectedToPlaylist(selectedSongs) }) { Text("Playliste ekle") }
                    OutlinedButton(enabled = selectedSongs.isNotEmpty(), onClick = { onAddSelectedToQueue(selectedSongs) }) { Text("Kuyruğa ekle") }
                }
            }
        }
        LibraryViewModeToggle(gridMode = gridMode, onGridModeChanged = { gridMode = it })
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val artworkSize = allSongsArtworkSizeDp(maxWidth.value).dp
            if (gridMode) {
                LaunchedEffect(gridState) {
                    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                        .distinctUntilChanged()
                        .collect { last ->
                            val current = latestState.value
                            if (!current.loading && current.errorMessage == null && current.hasMore && last >= current.songs.lastIndex - 5) latestNearEnd.value()
                        }
                }
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 156.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        gridItems(state.songs, key = { it.trackId }) { song -> SongGridCard(song = song, artworkResolver = artworkResolver, onSong = { if (selectionMode) selection = selection.toggle(song.trackId) else onSong(song) }, onLongPress = { startSelection(song) }, onEditSong = onEditSong, onAddToQueue = onAddToQueue, onPlayNext = onPlayNext, selected = song.trackId in selection.trackIds, selectionMode = selectionMode) }
                        item { if (state.loading) CircularProgressIndicator(color = Cyan, modifier = Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp) }
                    }
                    LibraryGridScrollbar(gridState, Modifier.padding(top = 8.dp, bottom = 8.dp))
                    if (showIndex) AlphabetIndexBar(activeLetter, { letter ->
                        activeLetter = letter
                        AlphabetIndex.resolve(letter, firstIndexByBucket)?.let { target -> indexScope.launch { gridState.scrollToItem(target) } }
                    })
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(state.songs, key = { it.trackId }) { song -> SongRow(song = song, artworkResolver = artworkResolver, artworkSize = artworkSize, onSong = { if (selectionMode) selection = selection.toggle(song.trackId) else onSong(song) }, onLongPress = { startSelection(song) }, onEditSong = onEditSong, onAddToQueue = onAddToQueue, onPlayNext = onPlayNext, onAddToPlaylist = { onAddSelectedToPlaylist(listOf(it)) }, selected = song.trackId in selection.trackIds, selectionMode = selectionMode) }
                        item {
                            if (state.loading) Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Cyan, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                    LibraryListScrollbar(listState, Modifier.padding(top = 8.dp, bottom = 8.dp))
                    if (showIndex) AlphabetIndexBar(activeLetter, { letter ->
                        activeLetter = letter
                        AlphabetIndex.resolve(letter, firstIndexByBucket)?.let { target -> indexScope.launch { listState.scrollToItem(target) } }
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongGridCard(
    song: LibrarySong,
    artworkResolver: AndroidArtworkResolver?,
    onSong: (LibrarySong) -> Unit,
    onLongPress: () -> Unit,
    onEditSong: (LibrarySong) -> Unit,
    onAddToQueue: (LibrarySong) -> Unit,
    onPlayNext: (LibrarySong) -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().border(if (selectionMode && selected) 1.dp else 0.dp, Cyan, RoundedCornerShape(8.dp)).alpha(if (song.isAccessible) 1f else .58f).combinedClickable(enabled = song.isAccessible, onClick = { onSong(song) }, onLongClick = onLongPress)) {
        if (artworkResolver != null) {
            LibraryArtwork(ArtworkRequest.Track(song.trackId), artworkResolver, Modifier.fillMaxWidth().aspectRatio(1f), "Album artwork", 192)
        } else {
            AlbumPlaceholder(Modifier.fillMaxWidth().aspectRatio(1f))
        }
        Text(song.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration(song.durationMs), color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { onEditSong(song) }) { Text("Düzenle", color = Cyan, fontSize = 10.sp) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(song: LibrarySong, artworkResolver: AndroidArtworkResolver?, artworkSize: androidx.compose.ui.unit.Dp, onSong: (LibrarySong) -> Unit, onLongPress: () -> Unit, onEditSong: (LibrarySong) -> Unit, onAddToQueue: (LibrarySong) -> Unit, onPlayNext: (LibrarySong) -> Unit, onAddToPlaylist: (LibrarySong) -> Unit, selected: Boolean, selectionMode: Boolean) {
    var actionsExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (selectionMode && selected) 1.dp else 0.dp, Cyan, RoundedCornerShape(8.dp))
            .alpha(if (song.isAccessible) 1f else .58f)
            .semantics { contentDescription = song.accessibilityDescription }
            .combinedClickable(enabled = song.isAccessible, onClick = { onSong(song) }, onLongClick = onLongPress)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (artworkResolver == null) {
            AlbumPlaceholder(Modifier.size(artworkSize))
        } else {
            LibraryArtwork(
                request = ArtworkRequest.Track(song.trackId),
                resolver = artworkResolver,
                modifier = Modifier.size(artworkSize).clip(RoundedCornerShape(7.dp)).border(1.dp, Line, RoundedCornerShape(7.dp)),
                contentDescription = "Album artwork",
                targetPx = 128,
            )
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(song.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(song.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                Text(" · ", color = Line, fontSize = 12.sp)
                Text(song.album, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MetadataBadge(song.metadataState)
                LocationBadge(song.locationState)
            }
        }
        Text(formatDuration(song.durationMs), color = Muted, fontSize = 12.sp, modifier = Modifier.width(42.dp))
        Box {
            Text("⋮", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable(enabled = song.isAccessible) { actionsExpanded = true }.padding(horizontal = 3.dp))
            DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }, modifier = Modifier.width(180.dp)) {
                DropdownMenuItem(text = { Text("Sonraki çal") }, onClick = { actionsExpanded = false; onPlayNext(song) })
                DropdownMenuItem(text = { Text("Kuyruğa ekle") }, onClick = { actionsExpanded = false; onAddToQueue(song) })
                DropdownMenuItem(text = { Text("Playliste ekle") }, onClick = { actionsExpanded = false; onAddToPlaylist(song) })
                DropdownMenuItem(text = { Text("Düzenle") }, onClick = { actionsExpanded = false; onEditSong(song) })
            }
        }
    }
}

@Composable
private fun AlbumPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(7.dp)).background(
            Brush.linearGradient(listOf(Color(0xFF172A31), Color(0xFF071014))),
        ).border(1.dp, Cyan.copy(alpha = .55f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) { Text("♪", color = Cyan, fontSize = 28.sp) }
}

@Composable
private fun MetadataBadge(state: LibraryMetadataState) {
    if (state == LibraryMetadataState.COMPLETE) return
    Text(
        text = if (state == LibraryMetadataState.MISSING) "METADATA MISSING" else "INCOMPLETE",
        color = Cyan,
        fontSize = 9.sp,
        letterSpacing = .5.sp,
    )
}

@Composable
private fun LocationBadge(state: LibraryLocationState) {
    if (state == LibraryLocationState.AVAILABLE || state == LibraryLocationState.UNKNOWN) return
    Text(state.name.replace('_', ' '), color = Muted, fontSize = 9.sp, letterSpacing = .5.sp)
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Cyan)
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Text("No songs in the library", color = Muted, fontSize = 16.sp)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Library unavailable\n$message", color = Color(0xFFFF8F8F), fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) { Text("RETRY", color = Cyan) }
        }
    }
}

private fun formatDuration(durationMs: Long?): String {
    if (durationMs == null || durationMs <= 0L) return "--:--"
    val totalSeconds = durationMs / 1000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

/** Whole-library title order (Turkish collation, symbols first, blank titles last). */
internal fun sortSongsByTitle(songs: List<LibrarySong>): List<LibrarySong> {
    val collator = AlphabetIndex.newCollator()
    return songs.sortedWith { a, b ->
        AlphabetIndex.compareTitles(a.title, b.title, collator).takeIf { it != 0 } ?: a.trackId.compareTo(b.trackId)
    }
}
