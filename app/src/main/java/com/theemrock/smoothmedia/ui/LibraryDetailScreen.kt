package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.artwork.AndroidArtworkResolver
import com.theemrock.smoothmedia.artwork.ArtworkRequest
import com.theemrock.smoothmedia.library.LibraryCollectionRow
import com.theemrock.smoothmedia.library.LibraryCollectionType
import com.theemrock.smoothmedia.library.LibrarySong
import com.theemrock.smoothmedia.playback.PlaybackEngineState

internal fun detailSongsStableOrder(songs: List<LibrarySong>): List<LibrarySong> =
    songs.sortedWith(compareBy<LibrarySong> { it.title.lowercase() }.thenBy { it.trackId })

@Composable
fun LibraryDetailScreen(
    type: LibraryCollectionType,
    row: LibraryCollectionRow,
    songs: List<LibrarySong>,
    artistAlbums: List<LibraryCollectionRow> = emptyList(),
    folderChildren: List<LibraryCollectionRow> = emptyList(),
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onDashboard: () -> Unit,
    onAlbumSelected: (LibraryCollectionRow) -> Unit,
    onArtistSelected: (LibraryCollectionRow) -> Unit = {},
    onFolderSelected: (LibraryCollectionRow) -> Unit = {},
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
    val detailTitle = if (type == LibraryCollectionType.FOLDERS && row.title.startsWith("content://")) "SAF kaynağı" else row.title
    val detailPath = row.key.takeUnless { it.startsWith("content://") }
    val albums = remember(songs, artistAlbums) {
        if (type == LibraryCollectionType.ARTISTS && artistAlbums.isNotEmpty()) artistAlbums
        else songs.groupBy { it.album to it.artist }.map { (key, tracks) ->
            LibraryCollectionRow(key.first + '\u001F' + key.second, key.first, key.second, tracks.size)
        }.sortedBy { it.title.lowercase() }
    }
    val artists = remember(songs) {
        songs.groupBy { it.artist.trim() }
            .filterKeys { it.isNotBlank() }
            .map { (artist, tracks) -> LibraryCollectionRow(artist, artist, null, tracks.size) }
            .sortedBy { it.title.lowercase() }
    }
    val albumListState = rememberLazyListState()
    val songListState = rememberLazyListState()
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        LibraryNavigationBar(onMenu = onMenu, onSearch = onSearch, onDashboard = onDashboard)
        Column(modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)) {
            Text(detailTitle, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${songs.size} şarkı", color = Color(0xFFAAB9C2), fontSize = 14.sp)
            if (type == LibraryCollectionType.ARTISTS) Text("${albums.size} albüm", color = Color(0xFFAAB9C2), fontSize = 14.sp)
            if (type == LibraryCollectionType.GENRES) Text("${albums.size} albüm · ${artists.size} sanatçı", color = Color(0xFFAAB9C2), fontSize = 14.sp)
            if (type == LibraryCollectionType.FOLDERS && detailPath != null && !detailPath.contains(row.title)) Text(detailPath, color = Color(0xFFAAB9C2), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (type == LibraryCollectionType.ARTISTS && albums.isNotEmpty()) {
            DetailSectionTitle("Albümler")
            Row(modifier = Modifier.weight(.34f)) {
              LazyColumn(state = albumListState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(albums, key = { it.key }) { album ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onAlbumSelected(album) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (artworkResolver != null) LibraryArtwork(ArtworkRequest.Album(album.title, album.subtitle.orEmpty()), artworkResolver, Modifier.size(52.dp).clip(RoundedCornerShape(7.dp)), "Album artwork", 128)
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(album.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${album.subtitle.orEmpty()} · ${album.trackCount} şarkı", color = Color(0xFFAAB9C2), fontSize = 12.sp)
                        }
                    }
                }
              }
              LibraryListScrollbar(albumListState, Modifier.padding(vertical = 4.dp))
            }
        }
        if (type == LibraryCollectionType.GENRES && albums.isNotEmpty()) {
            DetailSectionTitle("Albümler")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(albums, key = { it.key }) { album ->
                    Text(
                        "${album.title} · ${album.trackCount}",
                        color = Color(0xFF19C3E6),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onAlbumSelected(album) }.padding(vertical = 4.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        if (type == LibraryCollectionType.GENRES && artists.isNotEmpty()) {
            DetailSectionTitle("Sanatçılar")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(artists, key = { it.key }) { artist ->
                    Text(
                        "${artist.title} · ${artist.trackCount}",
                        color = Color(0xFF19C3E6),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onArtistSelected(artist) }.padding(vertical = 4.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        if (type == LibraryCollectionType.FOLDERS && folderChildren.isNotEmpty()) {
            DetailSectionTitle("Klasörler")
            LazyColumn(modifier = Modifier.weight(.28f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(folderChildren, key = { it.key }) { child ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onFolderSelected(child) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("▣", color = Color(0xFF19C3E6), modifier = Modifier.padding(end = 10.dp))
                        Text(child.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Text(if (type == LibraryCollectionType.GENRES) "Şarkılar" else "İçerik", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        if (songs.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Bu koleksiyonda kayıt bulunamadı", color = Color(0xFFAAB9C2)) }
        } else {
            Row(modifier = Modifier.weight(1f)) {
              LazyColumn(state = songListState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(detailSongsStableOrder(songs), key = { it.trackId }) { song ->
                    DetailSongRow(song, artworkResolver, onSong, onAddToQueue, onPlayNext)
                }
              }
              LibraryListScrollbar(songListState, Modifier.padding(vertical = 4.dp))
            }
        }
        if (playbackState != null && miniPlayerSong != null) MiniPlayer(miniPlayerSong, playbackState, onMiniPlayerOpen, onMiniPlayerPause, onMiniPlayerResume, onMiniPlayerNext, artworkResolver)
    }
}

@Composable
private fun DetailSectionTitle(title: String) {
    Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun DetailSongRow(song: LibrarySong, resolver: AndroidArtworkResolver?, onSong: (LibrarySong) -> Unit, onAdd: (LibrarySong) -> Unit, onNext: (LibrarySong) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().clickable(enabled = song.isAccessible) { onSong(song) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (resolver != null) LibraryArtwork(ArtworkRequest.Track(song.trackId), resolver, Modifier.size(46.dp), "Artwork", 96)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(song.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist + " · " + song.album, color = Color(0xFFAAB9C2), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatDetailDuration(song.durationMs ?: 0L), color = Color(0xFFAAB9C2), fontSize = 12.sp)
        IconButton(onClick = { expanded = true }) { Text("⋮", color = Color.White) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Sonraki çal") }, onClick = { expanded = false; onNext(song) })
            DropdownMenuItem(text = { Text("Kuyruğa ekle") }, onClick = { expanded = false; onAdd(song) })
        }
    }
}

private fun formatDetailDuration(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
