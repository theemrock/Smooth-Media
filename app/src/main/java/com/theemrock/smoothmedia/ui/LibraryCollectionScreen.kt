package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.library.LibraryCollectionRow
import com.theemrock.smoothmedia.library.LibraryCollectionType
import com.theemrock.smoothmedia.library.LibraryCollectionUiState
import com.theemrock.smoothmedia.library.artistFormatLabels
import com.theemrock.smoothmedia.library.artistStatsLine
import com.theemrock.smoothmedia.artwork.AndroidArtworkResolver
import com.theemrock.smoothmedia.artwork.ArtworkRequest
import kotlinx.coroutines.flow.distinctUntilChanged

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
    artworkResolver: AndroidArtworkResolver? = null,
) {
    Surface(color = CollectionInk, modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryNavigationBar(
                onMenu = onMenu,
                onSearch = onSearch,
                onDashboard = onDashboard,
                onSongs = onSongs,
                onCollection = onCollection,
                onSources = onSources,
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
                else -> CollectionGrid(state, onNearEnd, onCollectionSelected, artworkResolver)
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
    artworkResolver: AndroidArtworkResolver?,
) {
    val latestNearEnd = rememberUpdatedState(onNearEnd)
    val latestState = rememberUpdatedState(state)

    if (state.type == LibraryCollectionType.ARTISTS) {
        val listState = rememberLazyListState()
        LaunchedEffect(listState) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .distinctUntilChanged()
                .collect { last ->
                    val current = latestState.value
                    if (!current.loading && current.errorMessage == null && current.hasMore && last >= current.collections.lastIndex - 5) {
                        latestNearEnd.value()
                    }
                }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listItems(state.collections, key = { it.key }) { collection ->
                CollectionCard(collection, state.type, artworkResolver) { onCollection(collection) }
            }
            item {
                if (state.loading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CollectionCyan, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    } else {
        val gridState = rememberLazyGridState()
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
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = albumsGridMinCardWidthDp().dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            gridItems(state.collections, key = { it.key }) { collection ->
                CollectionCard(collection, state.type, artworkResolver) { onCollection(collection) }
            }
            item {
                if (state.loading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CollectionCyan, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(
    collection: LibraryCollectionRow,
    type: LibraryCollectionType,
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
                        request = ArtworkRequest.Album(collection.title, collection.subtitle.orEmpty()),
                        resolver = artworkResolver,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = "Album artwork for ${collection.title}",
                        targetPx = 256,
                    )
                } else {
                    Text(collectionGlyph(type), color = CollectionCyan, fontSize = 32.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            collection.title,
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

private fun collectionGlyph(type: LibraryCollectionType): String = when (type) {
    LibraryCollectionType.ALBUMS -> "●"
    LibraryCollectionType.ARTISTS -> "●"
    LibraryCollectionType.GENRES -> "◇"
    LibraryCollectionType.FOLDERS -> "■"
}
