package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.artwork.AndroidArtworkResolver
import com.theemrock.smoothmedia.artwork.ArtworkRequest
import com.theemrock.smoothmedia.library.LibrarySong
import com.theemrock.smoothmedia.playback.PlaybackEngineState
import com.theemrock.smoothmedia.playback.PlaybackStatus

@Composable
fun MiniPlayer(song: LibrarySong, state: PlaybackEngineState, onOpen: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onNext: () -> Unit, artworkResolver: AndroidArtworkResolver? = null) {
    val progress = if (state.durationMs > 0L) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF10161A)).border(1.dp, Color(0xFF26343B)).clickable(onClick = onOpen).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (artworkResolver != null) LibraryArtwork(ArtworkRequest.Track(song.trackId), artworkResolver, Modifier.size(46.dp), "Artwork: ${song.title}", 96)
            else Text("♪", color = SmoothMediaColors.Accent, fontSize = 26.sp)
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = SmoothMediaColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = if (state.status == PlaybackStatus.PLAYING) onPause else onResume) { Text(if (state.status == PlaybackStatus.PLAYING) "Ⅱ" else "▶", color = SmoothMediaColors.Accent, fontSize = 20.sp) }
            IconButton(onClick = onNext) { Text("›|", color = SmoothMediaColors.Accent, fontSize = 20.sp) }
        }
        LinearProgressIndicator(progress = { progress }, color = SmoothMediaColors.Accent, trackColor = Color(0xFF26343B), modifier = Modifier.fillMaxWidth().padding(start = 56.dp, top = 2.dp))
    }
}
