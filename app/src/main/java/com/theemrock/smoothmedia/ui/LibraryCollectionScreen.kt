package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.library.LibraryCollectionRow
import com.theemrock.smoothmedia.library.LibraryCollectionType
import com.theemrock.smoothmedia.library.LibraryCollectionUiState
import com.theemrock.smoothmedia.library.LibraryArtistAlbumPreview
import com.theemrock.smoothmedia.library.LibraryGenreArtworkPreview
import com.theemrock.smoothmedia.library.artistFormatLabels
import com.theemrock.smoothmedia.library.artistStatsLine
import com.theemrock.smoothmedia.artwork.AndroidArtworkResolver
import com.theemrock.smoothmedia.artwork.ArtworkRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val CollectionInk = Color(0xFF080B0D)
private val CollectionPanel = Color(0xFF10161A)
private val CollectionLine = Color(0xFF26343B)
private val CollectionMuted = Color(0xFFAAB9C2)
private val CollectionCyan = Color(0xFF19C3E6)

@Composable
fun LibraryCollectionScreen(
    state: LibraryCollectionUiState,
    onNearEnd: () -> Unit,
    onRetry: () -> Unit = {},
    onSongs: () -> Unit,
    onCollection: (LibraryCollectionType) -> Unit,
    onCollectionSelected: (LibraryCollectionRow) -> Unit = {},
    onSources: () -> Unit,
    onSearch: () -> Unit = {},
    onMenu: () -> Unit = {},
    onDashboard: () -> Unit = {},
    onSettings: () -> Unit = {},
    artistAlbumPreviews: Map<String, List<LibraryArtistAlbumPreview>> = emptyMap(),
    genreArtworkPreviews: Map<String, List<LibraryGenreArtworkPreview>> = emptyMap(),
    artworkResolver: AndroidArtworkResolver? = null,
) {
    Surface(color = CollectionInk, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryNavigationBar(
                onMenu = onMenu,
                onSearch = onSearch,
                onDashboard = onDashboard,
                onSongs = onSongs,
                onCollection = onCollection,
                onSources = onSources,
                onSettings = onSettings,
            )
            CollectionHeader(state.type, state.totalCollections)
            LibrarySectionTabs(
                selected = state.type,
                onSongs = onSongs,
                onCollection = onCollection,
            )
            when {
                state.errorMessage != null -> CollectionError(state.errorMessage, onRetry)
                !state.loaded || (state.loading && state.collections.isEmpty()) -> CollectionLoading()
                state.collections.isEmpty() -> CollectionEmpty(state.type)
                else -> CollectionGrid(state, onNearEnd, onCollectionSelected, artistAlbumPreviews, genreArtworkPreviews, artworkResolver)
            }
        }
    }
}

@Composable
private fun CollectionHeader(
    type: LibraryCollectionType,
    total: Int,
) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(collectionLabel(type).lowercase().replaceFirstChar { it.uppercase() }, color = Color.White, fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$total koleksiyon", color = CollectionMuted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun CollectionGrid(
    state: LibraryCollectionUiState,
    onNearEnd: () -> Unit,
    onCollection: (LibraryCollectionRow) -> Unit,
    artistAlbumPreviews: Map<String, List<LibraryArtistAlbumPreview>>,
    genreArtworkPreviews: Map<String, List<LibraryGenreArtworkPreview>>,
    artworkResolver: AndroidArtworkResolver?,
) {
    val latestNearEnd = rememberUpdatedState(onNearEnd)
    val latestState = rememberUpdatedState(state)

    if (state.type == LibraryCollectionType.ARTISTS) {
        ArtistCollectionContent(
            state = state,
            onNearEnd = latestNearEnd.value,
            onCollection = onCollection,
            artistAlbumPreviews = artistAlbumPreviews,
            artworkResolver = artworkResolver,
        )
    } else {
        var gridMode by rememberSaveable(state.type) { mutableStateOf(true) }
        val gridState = rememberLazyGridState()
        val listState = rememberLazyListState()
        LaunchedEffect(gridState) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .distinctUntilChanged()
                .collect { last ->
                    val current = latestState.value
                    if (!current.loading && current.errorMessage == null && current.hasMore && last >= current.collections.lastIndex - 5) {
                        latestNearEnd.value()
                    }
                }
        }
        LaunchedEffect(listState) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .distinctUntilChanged()
                .collect { last ->
                    val current = latestState.value
                    if (!current.loading && current.errorMessage == null && current.hasMore && last >= current.collections.lastIndex - 5) latestNearEnd.value()
                }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryViewModeToggle(gridMode = gridMode, onGridModeChanged = { gridMode = it })
            if (gridMode) {
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = albumsGridMinCardWidthDp().dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        gridItems(state.collections, key = { it.key }) { collection ->
                            CollectionCard(collection, state.type, genreArtworkPreviews[collection.title], artworkResolver) { onCollection(collection) }
                        }
                        item { if (state.loading) CircularProgressIndicator(color = CollectionCyan, modifier = Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp) }
                    }
                    LibraryGridScrollbar(gridState, Modifier.padding(top = 8.dp, bottom = 8.dp))
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listItems(state.collections, key = { it.key }) { collection ->
                            CollectionListRow(collection, state.type, genreArtworkPreviews[collection.title], artworkResolver) { onCollection(collection) }
                        }
                        item { if (state.loading) CircularProgressIndicator(color = CollectionCyan, modifier = Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp) }
                    }
                    LibraryListScrollbar(listState, Modifier.padding(top = 8.dp, bottom = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun CollectionListRow(
    collection: LibraryCollectionRow,
    type: LibraryCollectionType,
    genrePreviews: List<LibraryGenreArtworkPreview>?,
    artworkResolver: AndroidArtworkResolver?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (type == LibraryCollectionType.ALBUMS && artworkResolver != null) {
            LibraryArtwork(ArtworkRequest.Album(collection.title, collection.subtitle.orEmpty(), collection.key), artworkResolver, Modifier.size(64.dp), "Album artwork for ${collection.title}", 128)
        } else if (type == LibraryCollectionType.GENRES && artworkResolver != null && genrePreviews.orEmpty().isNotEmpty()) {
            LibraryArtwork(ArtworkRequest.Track(genrePreviews.orEmpty().first().trackId), artworkResolver, Modifier.size(64.dp), "Artwork for ${collection.title}", 128)
        } else {
            Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(CollectionPanel), contentAlignment = Alignment.Center) {
                Text(collectionGlyph(type), color = CollectionCyan, fontSize = 28.sp)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(collectionDisplayTitle(collection, type), color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            collection.subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, color = CollectionMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text("${collection.trackCount} şarkı", color = CollectionCyan, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ArtistCollectionContent(
    state: LibraryCollectionUiState,
    onNearEnd: () -> Unit,
    onCollection: (LibraryCollectionRow) -> Unit,
    artistAlbumPreviews: Map<String, List<LibraryArtistAlbumPreview>>,
    artworkResolver: AndroidArtworkResolver?,
) {
    var ascending by rememberSaveable { mutableStateOf(true) }
    var gridMode by rememberSaveable { mutableStateOf(false) }
    var activeLetter by remember { mutableStateOf<String?>(null) }
    val sortedRows = remember(state.collections, ascending) { sortArtistRows(state.collections, ascending) }
    val groups = remember(sortedRows) { groupArtistRows(sortedRows) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val groupIndexes = remember(groups) {
        buildMap {
            var index = 0
            groups.forEach { group ->
                put(group.letter, index)
                index += group.artists.size + 1
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                if (!state.loading && state.errorMessage == null && state.hasMore && last >= state.collections.lastIndex - 5) {
                    onNearEnd()
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LibraryViewModeToggle(gridMode = gridMode, onGridModeChanged = { gridMode = it }, label = "Sıralama")
            Text(
                if (ascending) "Sanatçı Adı (A-Z)" else "Sanatçı Adı (Z-A)",
                color = CollectionCyan,
                fontSize = 13.sp,
                modifier = Modifier.clickable { ascending = !ascending },
            )
        }
        if (gridMode) {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(sortedRows, key = { it.key }) { collection ->
                        ArtistGridCard(collection, artistAlbumPreviews[collection.title], artworkResolver) { onCollection(collection) }
                    }
                    item {
                        if (state.loading) CircularProgressIndicator(color = CollectionCyan, modifier = Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp)
                    }
                }
                LibraryGridScrollbar(gridState, Modifier.padding(top = 8.dp, bottom = 8.dp))
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 8.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    groups.forEach { group ->
                        item(key = "artist-header-${group.letter}") {
                            Text(
                                group.letter,
                                color = CollectionMuted,
                                fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth().background(CollectionPanel, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                        listItems(group.artists, key = { it.key }) { collection ->
                            ArtistListRow(collection, artistAlbumPreviews[collection.title], artworkResolver) { onCollection(collection) }
                        }
                    }
                    item {
                        if (state.loading) Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CollectionCyan, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                LibraryListScrollbar(listState, Modifier.padding(top = 8.dp, bottom = 8.dp))
                Column(
                    modifier = Modifier
                        .padding(end = 5.dp, top = 8.dp, bottom = 20.dp)
                        .pointerInput(groups) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                if (groups.isNotEmpty()) {
                                    val index = ((change.position.y / size.height.toFloat()) * groups.size).toInt().coerceIn(0, groups.lastIndex)
                                    val group = groups[index]
                                    activeLetter = group.letter
                                    groupIndexes[group.letter]?.let { target -> scope.launch { listState.animateScrollToItem(target) } }
                                }
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    groups.forEach { group ->
                        Text(
                            group.letter,
                            color = if (activeLetter == group.letter) Color.White else CollectionCyan,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .background(if (activeLetter == group.letter) CollectionCyan else Color.Transparent, RoundedCornerShape(4.dp))
                                .clickable {
                                    activeLetter = group.letter
                                    groupIndexes[group.letter]?.let { scope.launch { listState.animateScrollToItem(it) } }
                                },
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                if (!state.loading && state.errorMessage == null && state.hasMore && last >= state.collections.lastIndex - 5) onNearEnd()
            }
    }
}

@Composable
private fun ArtistListRow(
    collection: LibraryCollectionRow,
    previews: List<LibraryArtistAlbumPreview>?,
    artworkResolver: AndroidArtworkResolver?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArtistAvatar(previews?.firstOrNull(), artworkResolver, Modifier.size(52.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(collection.title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(collection.artistStatsLine, color = CollectionMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            previews.orEmpty().take(3).forEach { preview ->
                if (artworkResolver != null) {
                    LibraryArtwork(ArtworkRequest.Album(preview.album, preview.albumArtist), artworkResolver, Modifier.size(34.dp), "Album artwork for ${preview.album}", 96)
                }
            }
        }
        Text("⋮", color = Color.White, fontSize = 20.sp)
    }
}

@Composable
private fun ArtistGridCard(
    collection: LibraryCollectionRow,
    previews: List<LibraryArtistAlbumPreview>?,
    artworkResolver: AndroidArtworkResolver?,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ArtistAvatar(previews?.firstOrNull(), artworkResolver, Modifier.size(92.dp))
        Text(collection.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(collection.artistStatsLine, color = CollectionMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ArtistAvatar(
    preview: LibraryArtistAlbumPreview?,
    artworkResolver: AndroidArtworkResolver?,
    modifier: Modifier,
) {
    if (preview != null && artworkResolver != null) {
        LibraryArtwork(
            ArtworkRequest.Album(preview.album, preview.albumArtist),
            artworkResolver,
            modifier.clip(androidx.compose.foundation.shape.CircleShape),
            "Artist artwork from ${preview.album}",
            160,
        )
    } else {
        Box(modifier = modifier.clip(androidx.compose.foundation.shape.CircleShape).background(CollectionPanel), contentAlignment = Alignment.Center) {
            Text("●", color = CollectionCyan, fontSize = 28.sp)
        }
    }
}

@Composable
private fun CollectionCard(
    collection: LibraryCollectionRow,
    type: LibraryCollectionType,
    genrePreviews: List<LibraryGenreArtworkPreview>?,
    artworkResolver: AndroidArtworkResolver?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        if (type != LibraryCollectionType.ARTISTS) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (type == LibraryCollectionType.ALBUMS) Modifier.aspectRatio(1f) else Modifier.height(72.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF172A31), Color(0xFF071014))))
                    .border(1.dp, CollectionCyan.copy(alpha = .55f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (type == LibraryCollectionType.ALBUMS && artworkResolver != null) {
                    LibraryArtwork(
                        request = ArtworkRequest.Album(collection.title, collection.subtitle.orEmpty(), collection.key),
                        resolver = artworkResolver,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = "Album artwork for ${collection.title}",
                        targetPx = 256,
                    )
                } else if (type == LibraryCollectionType.GENRES && artworkResolver != null) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        genrePreviewIds(genrePreviews.orEmpty()).forEach { trackId ->
                            LibraryArtwork(
                                request = ArtworkRequest.Track(trackId),
                                resolver = artworkResolver,
                                modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(5.dp)),
                                contentDescription = "Artwork for ${collection.title}",
                                targetPx = 128,
                            )
                        }
                    }
                    if (genrePreviewIds(genrePreviews.orEmpty()).isEmpty()) {
                        Text(collectionGlyph(type), color = CollectionCyan, fontSize = 32.sp)
                    }
                } else {
                    Text(collectionGlyph(type), color = CollectionCyan, fontSize = 32.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            collectionDisplayTitle(collection, type),
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (type == LibraryCollectionType.ARTISTS) {
            Text(collection.artistStatsLine, color = CollectionMuted, fontSize = 11.sp)
            val formatLabels = collection.artistFormatLabels
            if (formatLabels.isNotEmpty()) {
                Text(
                    formatLabels.joinToString(" · "),
                    color = CollectionCyan,
                    fontSize = 10.sp,
                    letterSpacing = .4.sp,
                )
            }
        } else {
            collection.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = CollectionMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${collection.trackCount} şarkı", color = CollectionCyan, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CollectionLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = CollectionCyan)
    }
}

@Composable
private fun CollectionEmpty(type: LibraryCollectionType) {
    Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(
            if (type == LibraryCollectionType.FOLDERS) {
                "Folder paths are not available in this catalog"
            } else {
                "No ${collectionLabel(type).lowercase()} in the library"
            },
            color = CollectionMuted,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun CollectionError(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Collections unavailable\n$message", color = Color(0xFFFF8F8F))
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) { Text("RETRY", color = CollectionCyan) }
        }
    }
}

private fun collectionLabel(type: LibraryCollectionType): String = when (type) {
    LibraryCollectionType.ALBUMS -> "ALBUMS"
    LibraryCollectionType.ARTISTS -> "ARTISTS"
    LibraryCollectionType.GENRES -> "GENRES"
    LibraryCollectionType.FOLDERS -> "FOLDERS"
}

private fun collectionDisplayTitle(collection: LibraryCollectionRow, type: LibraryCollectionType): String {
    if (type != LibraryCollectionType.FOLDERS) return collection.title
    val raw = collection.title.trimEnd('/', '\\')
    if (raw.startsWith("content://")) return "SAF kaynağı"
    return raw.substringAfterLast('/').substringAfterLast('\\').ifBlank { raw }
}

private fun collectionGlyph(type: LibraryCollectionType): String = when (type) {
    LibraryCollectionType.ALBUMS -> "●"
    LibraryCollectionType.ARTISTS -> "●"
    LibraryCollectionType.GENRES -> "◇"
    LibraryCollectionType.FOLDERS -> "■"
}
