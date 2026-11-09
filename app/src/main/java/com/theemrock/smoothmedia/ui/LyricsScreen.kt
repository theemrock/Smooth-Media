package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.library.LibrarySong
import com.theemrock.smoothmedia.lyrics.CachedLyrics
import com.theemrock.smoothmedia.lyrics.LrcParser
import com.theemrock.smoothmedia.lyrics.LyricsMode

@Composable
fun LyricsScreen(
    song: LibrarySong,
    onBack: () -> Unit,
    cachedLyrics: CachedLyrics? = null,
    positionMs: Long = 0L,
    onSeek: (Long) -> Unit = {},
    onFetch: () -> Unit = {},
    fetchStatus: String? = null,
    fetching: Boolean = false,
    onSaveManual: (String) -> Unit = {},
) {
    var synced by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().background(SmoothMediaColors.Background).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹", color = Color.White, fontSize = 30.sp) }
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${song.artist} · ${song.album}", color = SmoothMediaColors.TextMuted, maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { synced = false }) { Text("Plain", color = if (!synced) SmoothMediaColors.Accent else SmoothMediaColors.TextMuted) }
            TextButton(onClick = { synced = true }) { Text("Synced", color = if (synced) SmoothMediaColors.Accent else SmoothMediaColors.TextMuted) }
        }
        val hasSynced = cachedLyrics?.mode == LyricsMode.SYNCED
        val swipeThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                var inZone = false
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { inZone = isInLyricsSwipeZone(it.x, size.width); total = 0f },
                    onDragEnd = {
                        if (inZone && abs(total) >= swipeThresholdPx) synced = total < 0f
                    },
                    onDragCancel = { inZone = false },
                    onHorizontalDrag = { _, delta -> total += delta },
                )
            },
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        if (cachedLyrics == null && editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Söz metni") },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Text(
                "Satırların başına [dk:sn.sn] yazarsan (örn. [01:23.45] söz) senkron olarak kaydedilir.",
                color = SmoothMediaColors.TextMuted,
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { onSaveManual(draft); editing = false }, enabled = draft.isNotBlank()) {
                    Text("Kaydet", color = SmoothMediaColors.Accent)
                }
                TextButton(onClick = { editing = false }) { Text("Vazgeç", color = SmoothMediaColors.TextMuted) }
            }
        } else if (cachedLyrics == null || (synced && !hasSynced)) {
            Text(
                if (synced) "Bu parça için senkronize söz verisi bulunmuyor." else "Bu parça için yerel söz verisi bulunmuyor.",
                color = SmoothMediaColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 48.dp),
            )
            if (cachedLyrics == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onFetch, enabled = !fetching) {
                        Text(
                            if (fetching) "Söz aranıyor…" else "Söz getir (internet)",
                            color = SmoothMediaColors.Accent,
                        )
                    }
                    TextButton(onClick = { editing = true }, enabled = !fetching) {
                        Text("Elle gir", color = SmoothMediaColors.Accent)
                    }
                }
            }
            fetchStatus?.let {
                Text(it, color = SmoothMediaColors.TextMuted, textAlign = TextAlign.Center, fontSize = 13.sp)
            }
        } else if (synced) {
            val parsed = LrcParser.parse(cachedLyrics.text)
            val active = parsed.activeLineAt(positionMs)
            val listState = rememberLazyListState()
            var recenter by remember { mutableIntStateOf(0) }
            FollowActiveLine(listState, active, recenter)
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(parsed.lines) { index, line ->
                    val isActive = index == active
                    // Layout stays at 20.sp; the active line is scaled to 24/20 so row heights never change mid-animation.
                    val scale by animateFloatAsState(if (isActive) 1.2f else 1f, animationSpec = tween(350), label = "lyricScale")
                    Text(
                        line.text,
                        color = if (isActive) SmoothMediaColors.Accent else Color.White,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable {
                            recenter++
                            onSeek(parsed.seekTargetForLine(index) ?: 0L)
                        }.padding(horizontal = 24.dp, vertical = 10.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(cachedLyrics.text.lines()) { _, line ->
                    Text(
                        line,
                        color = Color.White,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
        }
        }
    }
}

/**
 * Keeps [active] centred. Pauses while the user drags or flings and resumes the moment the list
 * settles.
 */
@Composable
private fun FollowActiveLine(listState: LazyListState, active: Int, recenter: Int) {
    var auto by remember { mutableStateOf(false) }
    val userScrolling = listState.isScrollInProgress && !auto
    fun centerOffset(): Int {
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo
        val height = estimateLyricsItemHeight(visible.firstOrNull { it.index == active }?.size, visible.map { it.size })
        return lyricsCenterScrollOffset(info.viewportEndOffset - info.viewportStartOffset, height)
    }
    LaunchedEffect(active, recenter, userScrolling) {
        if (active < 0 || userScrolling) return@LaunchedEffect
        auto = true
        try {
            listState.animateScrollToItem(active, centerOffset())
        } finally {
            auto = false
        }
    }
}
