package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.background
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

/** Glyph shown on the repeat button; ONE gets a "1" suffix so the active mode is visible. */
fun repeatModeGlyph(mode: RepeatMode): String = when (mode) {
    RepeatMode.OFF, RepeatMode.ALL -> "↻"
    RepeatMode.ONE -> "↻1"
}

/** Whether a shuffle or repeat control should be drawn as active. */
fun isRepeatActive(mode: RepeatMode): Boolean = mode != RepeatMode.OFF

@Composable
fun NowPlayingScreen(song: LibrarySong, state: PlaybackEngineState, onBack: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onSeek: (Long) -> Unit, onEqualizer: () -> Unit, onQueue: () -> Unit, onLyrics: () -> Unit = {}, onNext: () -> Unit, onPrevious: () -> Unit, shuffleEnabled: Boolean = false, repeatMode: RepeatMode = RepeatMode.OFF, onShuffle: () -> Unit, onRepeat: () -> Unit, artworkResolver: AndroidArtworkResolver? = null) {
    val progress = state.durationMs.coerceAtLeast(1L).toFloat()
    Column(modifier = Modifier.fillMaxSize().background(SmoothMediaColors.Background).padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Text("⌄", color = Color.White, fontSize = 30.sp) }
            Text("Şimdi çalıyor", color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 18.sp)
        }
        if (artworkResolver != null) LibraryArtwork(ArtworkRequest.Track(song.trackId), artworkResolver, Modifier.fillMaxWidth().weight(.52f).clip(RoundedCornerShape(18.dp)), "Artwork: ${song.title}", 720)
        else Spacer(Modifier.weight(.52f))
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text(song.title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(song.artist, color = SmoothMediaColors.TextMuted, fontSize = 18.sp, maxLines = 1)
            Text(song.album, color = SmoothMediaColors.TextMuted, fontSize = 14.sp, maxLines = 1)
        }
        Slider(value = state.positionMs.toFloat().coerceIn(0f, progress), onValueChange = { onSeek(it.toLong()) }, valueRange = 0f..progress, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatNowPlayingTime(state.positionMs), color = SmoothMediaColors.TextMuted); Text(formatNowPlayingTime(state.durationMs), color = SmoothMediaColors.TextMuted) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onShuffle) { Text("⇄", color = if (shuffleEnabled) SmoothMediaColors.Accent else SmoothMediaColors.TextMuted, fontSize = 26.sp) }
            IconButton(onClick = onPrevious) { Text("|◀", color = Color.White, fontSize = 24.sp) }
            IconButton(onClick = if (state.status == PlaybackStatus.PLAYING) onPause else onResume) { Text(if (state.status == PlaybackStatus.PLAYING) "Ⅱ" else "▶", color = SmoothMediaColors.Accent, fontSize = 34.sp) }
            IconButton(onClick = onNext) { Text("▶|", color = Color.White, fontSize = 24.sp) }
            IconButton(onClick = onRepeat) { Text(repeatModeGlyph(repeatMode), color = if (isRepeatActive(repeatMode)) SmoothMediaColors.Accent else SmoothMediaColors.TextMuted, fontSize = 26.sp) }
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
