package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.artwork.AndroidArtworkResolver
import com.theemrock.smoothmedia.artwork.ArtworkRequest
import com.theemrock.smoothmedia.library.LibrarySong
import com.theemrock.smoothmedia.playback.PlaybackEngineState
import com.theemrock.smoothmedia.playback.PlaybackStatus
import com.theemrock.smoothmedia.playback.RepeatMode

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(song: LibrarySong, isFlac: Boolean = false, state: PlaybackEngineState, onBack: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onSeek: (Long) -> Unit, onEqualizer: () -> Unit, onQueue: () -> Unit, onLyrics: () -> Unit = {}, onArtist: () -> Unit = {}, onAlbum: () -> Unit = {}, onNext: () -> Unit, onPrevious: () -> Unit, shuffleEnabled: Boolean = false, repeatMode: RepeatMode = RepeatMode.OFF, onShuffle: () -> Unit, onRepeat: () -> Unit, artworkResolver: AndroidArtworkResolver? = null) {
    val progress = state.durationMs.coerceAtLeast(1L).toFloat()
    val dismissThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    Column(modifier = Modifier.fillMaxSize().background(SmoothMediaColors.Background).padding(horizontal = 18.dp, vertical = 10.dp).pointerInput(Unit) {
        var armed = false
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { start -> armed = isNowPlayingDismissStart(start.y, size.height); total = 0f },
            onDragEnd = { if (armed && total >= dismissThresholdPx) onBack() },
            onDragCancel = { armed = false },
            onVerticalDrag = { _, delta -> total += delta },
        )
    }, horizontalAlignment = Alignment.CenterHorizontally) {
        if (artworkResolver != null) LibraryArtwork(ArtworkRequest.Track(song.trackId), artworkResolver, Modifier.fillMaxWidth().weight(.52f).clip(RoundedCornerShape(18.dp)), "Artwork: ${song.title}", 720)
        else Spacer(Modifier.weight(.52f))
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text(song.title, color = if (isFlac) SmoothMediaColors.Accent else Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth().basicMarquee())
            Text(song.artist, color = SmoothMediaColors.TextMuted, fontSize = 18.sp, maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth().clickable(onClick = onArtist).basicMarquee())
            Text(song.album, color = SmoothMediaColors.TextMuted, fontSize = 14.sp, maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth().clickable(onClick = onAlbum).basicMarquee())
        }
        Slider(value = state.positionMs.toFloat().coerceIn(0f, progress), onValueChange = { onSeek(it.toLong()) }, valueRange = 0f..progress, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatNowPlayingTime(state.positionMs), color = SmoothMediaColors.TextMuted); Text(formatNowPlayingTime(state.durationMs), color = SmoothMediaColors.TextMuted) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            ModeBox(shuffleEnabled, onShuffle) { ShuffleIcon(it, shuffleEnabled) }
            IconButton(onClick = onPrevious) { Text("|◀", color = Color.White, fontSize = 24.sp) }
            IconButton(onClick = if (state.status == PlaybackStatus.PLAYING) onPause else onResume) { Text(if (state.status == PlaybackStatus.PLAYING) "Ⅱ" else "▶", color = SmoothMediaColors.Accent, fontSize = 34.sp) }
            IconButton(onClick = onNext) { Text("▶|", color = Color.White, fontSize = 24.sp) }
            ModeBox(isRepeatActive(repeatMode), onRepeat, repeatBadge(repeatMode)) { RepeatIcon(it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ActionIcon("☷", "Kuyruk", onQueue)
            ActionIcon("♬", "Sözler", onLyrics)
            ActionIcon("≡", "Ekolayzır", onEqualizer)
        }
        if (state.status == PlaybackStatus.ERROR) Text(state.errorMessage ?: "Playback unavailable", color = SmoothMediaColors.Error)
    }
}

@Composable
private fun ActionIcon(icon: String, label: String, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = onClick) { Text(icon, color = SmoothMediaColors.TextPrimary, fontSize = 25.sp) }; Text(label, color = SmoothMediaColors.TextMuted, fontSize = 11.sp) }
}

private fun formatNowPlayingTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}

@Composable
private fun ModeBox(active: Boolean, onClick: () -> Unit, badge: String? = null, icon: @Composable (Color) -> Unit) {
    val tint = if (active) SmoothMediaColors.Accent else SmoothMediaColors.TextMuted
    Box(
        Modifier.size(60.dp).clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon(tint)
        if (badge != null) Text(badge, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 6.dp))
    }
}

private fun DrawScope.arrowHead(color: Color, a: Offset, b: Offset, c: Offset) {
    drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }, color)
}

@Composable
private fun ShuffleIcon(color: Color, shuffled: Boolean) {
    Canvas(Modifier.size(34.dp)) {
        val w = size.width; val h = size.height
        val stroke = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
        if (shuffled) {
            drawPath(Path().apply { moveTo(.08f * w, .28f * h); cubicTo(.5f * w, .28f * h, .5f * w, .72f * h, .78f * w, .72f * h) }, color, style = stroke)
            drawPath(Path().apply { moveTo(.08f * w, .72f * h); cubicTo(.5f * w, .72f * h, .5f * w, .28f * h, .78f * w, .28f * h) }, color, style = stroke)
            arrowHead(color, Offset(.74f * w, .58f * h), Offset(.74f * w, .86f * h), Offset(.95f * w, .72f * h))
            arrowHead(color, Offset(.74f * w, .14f * h), Offset(.74f * w, .42f * h), Offset(.95f * w, .28f * h))
        } else {
            drawLine(color, Offset(.08f * w, .30f * h), Offset(.78f * w, .30f * h), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(.08f * w, .70f * h), Offset(.78f * w, .70f * h), stroke.width, StrokeCap.Round)
            arrowHead(color, Offset(.74f * w, .16f * h), Offset(.74f * w, .44f * h), Offset(.95f * w, .30f * h))
            arrowHead(color, Offset(.74f * w, .56f * h), Offset(.74f * w, .84f * h), Offset(.95f * w, .70f * h))
        }
    }
}

@Composable
private fun RepeatIcon(color: Color) {
    Canvas(Modifier.size(34.dp)) {
        val w = size.width; val h = size.height
        val stroke = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(Path().apply { moveTo(.16f * w, .56f * h); lineTo(.16f * w, .32f * h); lineTo(.74f * w, .32f * h) }, color, style = stroke)
        drawPath(Path().apply { moveTo(.84f * w, .44f * h); lineTo(.84f * w, .68f * h); lineTo(.26f * w, .68f * h) }, color, style = stroke)
        arrowHead(color, Offset(.70f * w, .20f * h), Offset(.70f * w, .44f * h), Offset(.90f * w, .32f * h))
        arrowHead(color, Offset(.30f * w, .56f * h), Offset(.30f * w, .80f * h), Offset(.10f * w, .68f * h))
    }
}
