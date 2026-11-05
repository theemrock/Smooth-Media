package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.artwork.AndroidArtworkResolver
import com.theemrock.smoothmedia.artwork.ArtworkRequest
import com.theemrock.smoothmedia.playback.PlaybackQueueState
import com.theemrock.smoothmedia.playback.QueueItem
import com.theemrock.smoothmedia.playback.QueueMode
import com.theemrock.smoothmedia.playback.RepeatMode

@Composable
fun QueueScreen(
    state: PlaybackQueueState,
    onBack: () -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onSelect: (Int) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    artworkResolver: AndroidArtworkResolver? = null,
) {
    val current = state.currentIndex.takeIf { it in state.items.indices }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SmoothMediaColors.Background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Text("⌄", color = Color.White, fontSize = 30.sp) }
            Text(
                "Kuyruk",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onClear, enabled = state.items.isNotEmpty()) {
                Text("Temizle", color = if (state.items.isNotEmpty()) SmoothMediaColors.Error else SmoothMediaColors.TextMuted)
            }
        }
        Text(
            queueSubtitle(state),
            color = SmoothMediaColors.TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            textAlign = TextAlign.Center,
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            QueueChip("⇄ Karışık", active = state.shuffle, onClick = onShuffle)
            QueueChip("↻ ${repeatLabel(state.repeat)}", active = state.repeat != RepeatMode.OFF, onClick = onRepeat)
            QueueChip("|◀", active = false, enabled = current != null && current > 0, onClick = onPrevious)
            QueueChip("▶|", active = false, enabled = state.items.isNotEmpty(), onClick = onNext)
        }

        if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Kuyrukta şarkı yok", color = SmoothMediaColors.TextMuted, fontSize = 16.sp)
            }
            return@Column
        }

        val listState = rememberLazyListState()
        // Open on the playing song; the songs before it sit above and stay reachable by scrolling up.
        LaunchedEffect(Unit) {
            if (current != null && current > 0) listState.scrollToItem(current + 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (current != null && current > 0) {
                item(key = "header-prev") { SectionHeader("Önceki · $current") }
                itemsIndexed(
                    state.items.subList(0, current),
                    key = { _, item -> item.entryId },
                ) { index, item ->
                    QueueRow(
                        index = index,
                        item = item,
                        isCurrent = false,
                        lastIndex = state.items.lastIndex,
                        artworkResolver = artworkResolver,
                        onSelect = onSelect,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onRemove = onRemove,
                    )
                }
            }
            if (current != null) {
                item(key = "header-now") { SectionHeader("Şimdi çalıyor") }
                item(key = state.items[current].entryId) {
                    QueueRow(
                        index = current,
                        item = state.items[current],
                        isCurrent = true,
                        lastIndex = state.items.lastIndex,
                        artworkResolver = artworkResolver,
                        onSelect = onSelect,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onRemove = onRemove,
                    )
                }
            }

            val upcomingStart = (current ?: -1) + 1
            if (upcomingStart < state.items.size) {
                item(key = "header-next") {
                    SectionHeader("Sıradakiler · ${state.items.size - upcomingStart}")
                }
                itemsIndexed(
                    state.items.subList(upcomingStart, state.items.size),
                    key = { _, item -> item.entryId },
                ) { offset, item ->
                    val index = upcomingStart + offset
                    QueueRow(
                        index = index,
                        item = item,
                        isCurrent = false,
                        lastIndex = state.items.lastIndex,
                        artworkResolver = artworkResolver,
                        onSelect = onSelect,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onRemove = onRemove,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = SmoothMediaColors.Accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
    )
}

@Composable
private fun QueueRow(
    index: Int,
    item: QueueItem,
    isCurrent: Boolean,
    lastIndex: Int,
    artworkResolver: AndroidArtworkResolver?,
    onSelect: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val title = item.title.ifBlank { item.trackId }
    val subtitle = listOf(item.artist, item.album, formatQueueDuration(item.durationMs))
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) SmoothMediaColors.SurfaceVariant else Color.Transparent)
            .clickable { onSelect(index) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QueueThumb(item, artworkResolver)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (isCurrent) SmoothMediaColors.Accent else SmoothMediaColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    color = SmoothMediaColors.TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { onMoveUp(index) }, enabled = index > 0, modifier = Modifier.size(36.dp)) {
            Text("▲", color = if (index > 0) SmoothMediaColors.TextMuted else SmoothMediaColors.Divider, fontSize = 12.sp)
        }
        IconButton(onClick = { onMoveDown(index) }, enabled = index < lastIndex, modifier = Modifier.size(36.dp)) {
            Text("▼", color = if (index < lastIndex) SmoothMediaColors.TextMuted else SmoothMediaColors.Divider, fontSize = 12.sp)
        }
        IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(36.dp)) {
            Text("✕", color = SmoothMediaColors.Error, fontSize = 14.sp)
        }
    }
}

@Composable
private fun QueueThumb(item: QueueItem, artworkResolver: AndroidArtworkResolver?) {
    val size = Modifier.size(46.dp)
    if (artworkResolver != null) {
        LibraryArtwork(
            request = ArtworkRequest.Track(item.trackId),
            resolver = artworkResolver,
            modifier = size,
            contentDescription = "Kapak: ${item.title.ifBlank { item.trackId }}",
            targetPx = 128,
        )
    } else {
        Box(
            size.clip(RoundedCornerShape(8.dp)).background(SmoothMediaColors.SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("♪", color = SmoothMediaColors.Accent, fontSize = 18.sp)
        }
    }
}

@Composable
private fun QueueChip(label: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = when {
        !enabled -> SmoothMediaColors.Divider
        active -> SmoothMediaColors.Accent
        else -> SmoothMediaColors.TextPrimary
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) SmoothMediaColors.SurfaceVariant else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = tint, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun repeatLabel(mode: RepeatMode): String = when (mode) {
    RepeatMode.OFF -> "Tekrar yok"
    RepeatMode.ALL -> "Tümünü tekrar"
    RepeatMode.ONE -> "Parçayı tekrar"
}

private fun queueSubtitle(state: PlaybackQueueState): String {
    val count = state.items.size
    val countText = when (count) {
        0 -> "Boş"
        1 -> "1 şarkı"
        else -> "$count şarkı"
    }
    val source = if (state.mode == QueueMode.GHOST) " · kütüphane sırası" else ""
    return countText + source
}

private fun formatQueueDuration(durationMs: Long?): String {
    if (durationMs == null || durationMs <= 0L) return ""
    val totalSeconds = durationMs / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
