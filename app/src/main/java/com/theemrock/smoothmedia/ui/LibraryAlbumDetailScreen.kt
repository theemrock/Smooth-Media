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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.artwork.AndroidArtworkResolver
import com.theemrock.smoothmedia.artwork.ArtworkRequest
import com.theemrock.smoothmedia.library.LibraryCollectionRow
import com.theemrock.smoothmedia.library.LibrarySong
import com.theemrock.smoothmedia.playback.PlaybackEngineState

data class LibraryAlbumDetailData(
    val album: LibraryCollectionRow,
    val songs: List<LibrarySong>,
)

internal fun albumDetailStableOrder(songs: List<LibrarySong>): List<LibrarySong> =
    songs.sortedWith(compareBy<LibrarySong> { it.title.lowercase() }.thenBy { it.trackId })

internal fun albumDetailActionLabels(hasSongs: Boolean): List<String> =
    if (hasSongs) listOf("Albümü Çal") else emptyList()

internal fun albumDetailTabLabels(): List<String> =
    listOf("Şarkılar", "Detaylar", "Benzer Albümler")

@Composable
fun LibraryAlbumDetailScreen(
    data: LibraryAlbumDetailData,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onDashboard: () -> Unit,
    onPlayAlbum: () -> Unit = {},
    onSong: (LibrarySong) -> Unit,
    onAddToQueue: (LibrarySong) -> Unit,
    onPlayNext: (LibrarySong) -> Unit,
    playbackState: PlaybackEngineState? = null,
    miniPlayerSong: LibrarySong? = null,
    onMiniPlayerOpen: () -> Unit = {},
    onMiniPlayerPause: () -> Unit = {},
    onMiniPlayerResume: () -> Unit = {},
    onMiniPlayerNext: () -> Unit = {},
    artworkResolver: AndroidArtworkResolver? = null,
) {
    val totalDuration = data.songs.sumOf { it.durationMs ?: 0L }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SmoothMediaColors.Background),
    ) {
        LibraryNavigationBar(onMenu = onMenu, onSearch = onSearch, onDashboard = onDashboard)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { AlbumDetailHeader(data, totalDuration, artworkResolver) }
            if (data.songs.isNotEmpty()) item { AlbumDetailActions(onPlayAlbum) }
            item { AlbumDetailTabs() }
            if (data.songs.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Text("Bu albümde gösterilecek şarkı yok", color = SmoothMediaColors.TextMuted)
                    }
                }
            } else {
                items(albumDetailStableOrder(data.songs), key = { it.trackId }) { song ->
                    AlbumDetailSongRow(song, artworkResolver, onSong, onAddToQueue, onPlayNext)
                }
                item { AlbumDetailSummary(data.songs.size, totalDuration) }
            }
        }
        if (playbackState != null && miniPlayerSong != null) {
            MiniPlayer(miniPlayerSong, playbackState, onMiniPlayerOpen, onMiniPlayerPause, onMiniPlayerResume, onMiniPlayerNext, artworkResolver)
        }
    }
}

@Composable
private fun AlbumDetailHeader(data: LibraryAlbumDetailData, totalDuration: Long, artworkResolver: AndroidArtworkResolver?) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (artworkResolver != null) {
            LibraryArtwork(
                request = ArtworkRequest.Album(data.album.title, data.album.subtitle.orEmpty()),
                resolver = artworkResolver,
                modifier = Modifier.size(144.dp).clip(RoundedCornerShape(10.dp)),
                contentDescription = "Albüm kapağı",
                targetPx = 360,
            )
        } else {
            Box(modifier = Modifier.size(144.dp).clip(RoundedCornerShape(10.dp)).background(SmoothMediaColors.SurfaceVariant), contentAlignment = Alignment.Center) {
                Text("♫", color = SmoothMediaColors.Accent, fontSize = 36.sp)
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(data.album.title, color = SmoothMediaColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            data.album.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = SmoothMediaColors.TextMuted, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("${data.songs.size} şarkı · ${formatAlbumDuration(totalDuration)}", color = SmoothMediaColors.TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AlbumDetailActions(onPlayAlbum: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SmoothMediaColors.Accent).clickable(onClick = onPlayAlbum).padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("▶  Albümü Çal", color = SmoothMediaColors.Background, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun AlbumDetailTabs() {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        albumDetailTabLabels().forEachIndexed { index, label ->
            Column(modifier = Modifier.weight(1f).padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = if (index == 0) SmoothMediaColors.Accent else SmoothMediaColors.TextMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(if (index == 0) 2.dp else 1.dp).background(if (index == 0) SmoothMediaColors.Accent else SmoothMediaColors.Divider))
            }
        }
    }
}

@Composable
private fun AlbumDetailSummary(count: Int, durationMs: Long) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        AlbumSummaryValue("♫", count.toString(), "şarkı")
        AlbumSummaryValue("◷", formatAlbumDuration(durationMs), "toplam süre")
    }
}

@Composable
private fun AlbumSummaryValue(icon: String, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = SmoothMediaColors.Accent, fontSize = 22.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(value, color = SmoothMediaColors.TextPrimary, fontSize = 14.sp)
            Text(label, color = SmoothMediaColors.TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AlbumDetailSongRow(
    song: LibrarySong,
    artworkResolver: AndroidArtworkResolver?,
    onSong: (LibrarySong) -> Unit,
    onAddToQueue: (LibrarySong) -> Unit,
    onPlayNext: (LibrarySong) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().clickable(enabled = song.isAccessible) { onSong(song) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (artworkResolver != null) LibraryArtwork(ArtworkRequest.Track(song.trackId), artworkResolver, Modifier.size(48.dp), "Şarkı kapağı", 128)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(song.title, color = SmoothMediaColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = SmoothMediaColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatAlbumDuration(song.durationMs ?: 0L), color = SmoothMediaColors.TextMuted, fontSize = 12.sp)
        Box {
            IconButton(onClick = { expanded = true }) { Text("⋮", color = SmoothMediaColors.TextPrimary, fontSize = 22.sp) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Sonraki çal") }, onClick = { expanded = false; onPlayNext(song) })
                DropdownMenuItem(text = { Text("Kuyruğa ekle") }, onClick = { expanded = false; onAddToQueue(song) })
            }
        }
    }
}

private fun formatAlbumDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
