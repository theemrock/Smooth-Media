package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    artworkResolver: AndroidArtworkResolver? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Text("‹", color = Color.White) }
            Text("NOW PLAYING", color = Color(0xFF19C3E6), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Row {
                IconButton(onClick = onQueue) { Text("Q", color = Color(0xFF19C3E6)) }
                IconButton(onClick = onEqualizer) { Text("EQ", color = Color(0xFF19C3E6)) }
            }
        }
        if (artworkResolver != null) {
            LibraryArtwork(
                request = ArtworkRequest.Track(song.trackId),
                resolver = artworkResolver,
                modifier = Modifier.size(280.dp),
                contentDescription = "Artwork: ${song.title}",
                targetPx = 640,
            )
        } else {
            Text("♪", color = Color(0xFF19C3E6), fontSize = 96.sp)
        }
        Text(song.title, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Text("${song.artist} · ${song.album}", color = Color(0xFFAAB9C2), textAlign = TextAlign.Center)
        Slider(
            value = state.positionMs.toFloat().coerceIn(0f, state.durationMs.coerceAtLeast(1L).toFloat()),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("${formatNowPlayingTime(state.positionMs)} / ${formatNowPlayingTime(state.durationMs)}", color = Color(0xFFAAB9C2))
        Button(onClick = if (state.status == PlaybackStatus.PLAYING) onPause else onResume) {
            Text(if (state.status == PlaybackStatus.PLAYING) "Pause" else "Play")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = onPrevious) { Text("Previous") }
            Button(onClick = onNext) { Text("Next") }
            Button(onClick = onShuffle) { Text("Shuffle") }
            Button(onClick = onRepeat) { Text("Repeat") }
        }
        if (state.status == PlaybackStatus.ERROR) {
            Text(state.errorMessage ?: "Playback unavailable", color = Color(0xFFFF8A80))
        }
    }
}

private fun formatNowPlayingTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
