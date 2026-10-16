package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
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
fun MiniPlayer(
    song: LibrarySong,
    state: PlaybackEngineState,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onNext: () -> Unit,
    artworkResolver: AndroidArtworkResolver? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF10161A))
            .border(1.dp, Color(0xFF26343B))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (artworkResolver != null) {
            LibraryArtwork(
                request = ArtworkRequest.Track(song.trackId),
                resolver = artworkResolver,
                modifier = Modifier.size(44.dp),
                contentDescription = "Artwork: ${song.title}",
                targetPx = 96,
            )
        } else {
            Text("♪", color = Color(0xFF19C3E6))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color(0xFFAAB9C2), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = if (state.status == PlaybackStatus.PLAYING) onPause else onResume) {
            Text(if (state.status == PlaybackStatus.PLAYING) "Ⅱ" else "▶", color = Color(0xFF19C3E6))
        }
        IconButton(onClick = onNext) { Text("›|", color = Color(0xFF19C3E6)) }
    }
}
