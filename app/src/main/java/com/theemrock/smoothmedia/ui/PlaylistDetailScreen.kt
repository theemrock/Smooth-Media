package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.theemrock.smoothmedia.artwork.AndroidArtworkResolver
import com.theemrock.smoothmedia.artwork.ArtworkRequest
import com.theemrock.smoothmedia.library.LibraryCollectionType
import com.theemrock.smoothmedia.library.LibrarySong
import com.theemrock.smoothmedia.playback.PlaybackEngineState
import kotlinx.coroutines.launch

private val PlaylistInk = Color(0xFF080B0D)
private val PlaylistMuted = Color(0xFFAAB9C2)
private val PlaylistCyan = Color(0xFF19C3E6)
private val PlaylistDragged = Color(0xFF1B2A31)

/**
 * One playlist: songs in the order they sit in the playlist. Long press a row, then drag it to a
 * new place; the new order is reported through [onReorder] when the finger lifts.
 */
@Composable
fun PlaylistDetailScreen(
    title: String,
    songs: List<LibrarySong>,
    loading: Boolean,
    activeTrackId: String?,
    onSong: (LibrarySong) -> Unit,
    onReorder: (List<LibrarySong>) -> Unit,
    counts: LibraryCategoryCounts,
    onSearch: () -> Unit,
    onDashboard: () -> Unit,
    onSongs: () -> Unit,
    onCollection: (LibraryCollectionType) -> Unit,
    onSources: () -> Unit,
    onSettings: () -> Unit,
    playbackState: PlaybackEngineState? = null,
    miniPlayerSong: LibrarySong? = null,
    onMiniPlayerOpen: () -> Unit = {},
    onMiniPlayerPause: () -> Unit = {},
    onMiniPlayerResume: () -> Unit = {},
    onMiniPlayerNext: () -> Unit = {},
    artworkResolver: AndroidArtworkResolver? = null,
) {
    Surface(color = PlaylistInk, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryChrome(
                counts = counts,
                selected = LibraryChromeTab.PLAYLISTS,
                onSearch = onSearch,
                onDashboard = onDashboard,
                onSongs = onSongs,
                onCollection = onCollection,
                onSources = onSources,
                onSettings = onSettings,
            )
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${songs.size} şarkı · sıralamak için basılı tutup sürükle",
                    color = PlaylistMuted,
                    fontSize = 12.sp,
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PlaylistCyan)
                    }
                    songs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Bu playlist boş", color = PlaylistMuted, fontSize = 16.sp)
                    }
                    else -> ReorderableSongList(songs, activeTrackId, onSong, onReorder, artworkResolver)
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
private fun ReorderableSongList(
    songs: List<LibrarySong>,
    activeTrackId: String?,
    onSong: (LibrarySong) -> Unit,
    onReorder: (List<LibrarySong>) -> Unit,
    artworkResolver: AndroidArtworkResolver?,
) {
    var items by remember(songs) { mutableStateOf(songs) }
    val latestItems = rememberUpdatedState(items)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val edgePx = with(LocalDensity.current) { 56.dp.toPx() }
    val scrollStepPx = with(LocalDensity.current) { 10.dp.toPx() }

    fun onDrag(delta: Float) {
        val id = draggedId ?: return
        dragOffset += delta
        val layout = listState.layoutInfo
        val dragged = layout.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        val from = items.indexOfFirst { it.trackId == id }
        // Only swap while the layout already reflects the list; otherwise one pass would swap twice.
        if (dragged.index == from) {
            val center = dragged.offset + dragOffset + dragged.size / 2f
            val target = layout.visibleItemsInfo.firstOrNull { it.key != id && center >= it.offset && center <= it.offset + it.size }
            if (target != null) {
                val newOffset = if (target.index < from) target.offset else target.offset + target.size - dragged.size
                items = items.toMutableList().apply { add(target.index, removeAt(from)) }
                dragOffset += dragged.offset - newOffset
            }
        }
        val top = dragged.offset + dragOffset
        val bottom = top + dragged.size
        val step = when {
            top < layout.viewportStartOffset + edgePx / 2f -> -scrollStepPx
            bottom > layout.viewportEndOffset - edgePx / 2f -> scrollStepPx
            else -> 0f
        }
        if (step != 0f) scope.launch { dragOffset += listState.scrollBy(step) }
    }

    fun finish() {
        if (draggedId == null) return
        draggedId = null
        dragOffset = 0f
        onReorder(latestItems.value)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(items, key = { _, song -> song.trackId }) { _, song ->
            val dragging = song.trackId == draggedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(song.trackId) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggedId = song.trackId; dragOffset = 0f },
                            onDrag = { change, amount -> change.consume(); onDrag(amount.y) },
                            onDragEnd = { finish() },
                            onDragCancel = { finish() },
                        )
                    }
                    .then(
                        if (dragging) Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset; scaleX = 1.02f; scaleY = 1.02f }
                        else Modifier.animateItem(),
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (dragging) PlaylistDragged else Color.Transparent)
                    .clickable { onSong(song) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (artworkResolver != null) {
                    LibraryArtwork(
                        ArtworkRequest.Track(song.trackId),
                        artworkResolver,
                        Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                        "Artwork: ${song.title}",
                        128,
                    )
                } else {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF10161A)))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title,
                        color = if (song.trackId == activeTrackId) PlaylistCyan else Color.White,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · "),
                        color = PlaylistMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(formatPlaylistDuration(song.durationMs), color = PlaylistMuted, fontSize = 12.sp)
            }
        }
    }
}

internal fun formatPlaylistDuration(durationMs: Long?): String {
    val seconds = (durationMs ?: return "").coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
