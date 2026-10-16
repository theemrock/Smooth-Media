package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.artwork.AndroidArtworkResolver
import com.theemrock.smoothmedia.artwork.ArtworkRequest
import com.theemrock.smoothmedia.library.LibrarySong
import com.theemrock.smoothmedia.playback.PlaybackEngineState
import com.theemrock.smoothmedia.playback.PlaybackStatus

@Composable
fun NowPlayingScreen(
    song: LibrarySong,
    state: PlaybackEngineState,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSeek: (Long) -> Unit,
    onEqualizer: () -> Unit,
    onQueue: () -> Unit,
    onLyrics: () -> Unit = {},
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    artworkResolver: AndroidArtworkResolver? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Text("⌄", color = SmoothMediaColors.TextPrimary, fontSize = 28.sp) }
            Text("Şimdi çalıyor", color = SmoothMediaColors.TextPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 18.sp)
            Row {
                IconButton(onClick = onQueue) { Text("☷", color = SmoothMediaColors.Accent, fontSize = 22.sp) }
                IconButton(onClick = onLyrics) { Text("♫", color = SmoothMediaColors.Accent, fontSize = 22.sp) }
            }
        }
        if (artworkResolver != null) {
            LibraryArtwork(
                request = ArtworkRequest.Track(song.trackId),
                resolver = artworkResolver,
                modifier = Modifier.fillMaxWidth().size(320.dp),
                contentDescription = "Artwork: ${song.title}",
                targetPx = 640,
            )
        } else {
            Text("♫", color = SmoothMediaColors.Accent, fontSize = 96.sp)
        }
        Text(song.title, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, fontSize = 24.sp)
        Text("${song.artist} · ${song.album}", color = SmoothMediaColors.TextMuted, textAlign = TextAlign.Center)
        Slider(
            value = state.positionMs.toFloat().coerceIn(0f, state.durationMs.coerceAtLeast(1L).toFloat()),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatNowPlayingTime(state.positionMs), color = SmoothMediaColors.TextMuted)
            Text(formatNowPlayingTime(state.durationMs), color = SmoothMediaColors.TextMuted)
        }
        IconButton(onClick = if (state.status == PlaybackStatus.PLAYING) onPause else onResume) {
            Text(if (state.status == PlaybackStatus.PLAYING) "Ⅱ" else "▶", color = SmoothMediaColors.Accent, fontSize = 38.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = onShuffle) { Text("⇄", color = SmoothMediaColors.Accent, fontSize = 26.sp) }
            IconButton(onClick = onPrevious) { Text("|◀", color = SmoothMediaColors.TextPrimary, fontSize = 24.sp) }
            IconButton(onClick = onNext) { Text("▶|", color = SmoothMediaColors.TextPrimary, fontSize = 24.sp) }
            IconButton(onClick = onRepeat) { Text("↻", color = SmoothMediaColors.Accent, fontSize = 26.sp) }
            IconButton(onClick = onEqualizer) { Text("≡", color = SmoothMediaColors.TextPrimary, fontSize = 26.sp) }
        }
        if (state.status == PlaybackStatus.ERROR) {
            Text(state.errorMessage ?: "Playback unavailable", color = SmoothMediaColors.Error)
        }
    }
}

private fun formatNowPlayingTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
