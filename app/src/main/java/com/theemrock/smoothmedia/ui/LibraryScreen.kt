package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.library.LibraryLocationState
import com.theemrock.smoothmedia.library.LibraryCollectionType
import com.theemrock.smoothmedia.library.LibraryMetadataState
import com.theemrock.smoothmedia.library.LibrarySong
import com.theemrock.smoothmedia.library.LibraryUiState
import kotlinx.coroutines.flow.distinctUntilChanged

private val Ink = Color(0xFF080B0D)
private val Panel = Color(0xFF10161A)
private val Line = Color(0xFF26343B)
private val Muted = Color(0xFFAAB9C2)
private val Cyan = Color(0xFF19C3E6)

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onNearEnd: () -> Unit,
    onRetry: () -> Unit = {},
    onSources: () -> Unit,
    onCollection: (LibraryCollectionType) -> Unit = {},
) {
    Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryHeader(state.heading, state.totalTracks, onSources, onCollection)
            when {
                state.errorMessage != null -> ErrorState(state.errorMessage, onRetry)
                !state.loaded || (state.loading && state.songs.isEmpty()) -> LoadingState()
                state.songs.isEmpty() -> EmptyState()
                else -> SongsList(state, onNearEnd)
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    heading: String,
    totalTracks: Int,
    onSources: () -> Unit,
    onCollection: (LibraryCollectionType) -> Unit,
) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "S M O O T H",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 5.sp,
                )
                Text(
                    text = "M E D I A",
                    color = Muted,
                    fontSize = 10.sp,
                    letterSpacing = 4.sp,
                )
            }
            OutlinedButton(onClick = onSources, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text("SOURCES", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text("SONGS", color = Cyan, fontSize = 13.sp, letterSpacing = 2.sp)
                Text(
                    text = heading,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text("$totalTracks TRACKS", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Cyan))
        LibrarySectionTabs(selected = null, onSongs = {}, onCollection = onCollection)
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
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Text(label, color = if (selected) Cyan else Muted, fontSize = 9.sp, letterSpacing = .4.sp)
    }
}

@Composable
private fun SongsList(state: LibraryUiState, onNearEnd: () -> Unit) {
    val listState = rememberLazyListState()
    val latestNearEnd = rememberUpdatedState(onNearEnd)
    val latestState = rememberUpdatedState(state)
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
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.songs, key = { it.trackId }) { song -> SongRow(song) }
        item {
            if (state.loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Cyan, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: LibrarySong) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(Panel, Ink)))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumPlaceholder()
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(song.album, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                MetadataBadge(song.metadataState)
                LocationBadge(song.locationState)
            }
        }
        Text(formatDuration(song.durationMs), color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun AlbumPlaceholder() {
    Box(
        modifier = Modifier.size(58.dp).clip(RoundedCornerShape(8.dp)).background(
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
