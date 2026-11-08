package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.library.LibraryCollectionRow
import com.theemrock.smoothmedia.library.LibrarySearchPage
import com.theemrock.smoothmedia.library.LibrarySearchUiState

/** Result categories shown as a fixed tab strip under the search box; the selected tab decides which results are listed. */
enum class SearchTab(val label: String) {
    SONGS("Şarkılar"),
    ARTISTS("Sanatçılar"),
    ALBUMS("Albümler"),
    GENRES("Türler"),
    FOLDERS("Klasörler"),
    LYRICS("Şarkı sözleri");

    fun count(results: LibrarySearchPage): Int = when (this) {
        SONGS -> results.songs.size
        ARTISTS -> results.artists.size
        ALBUMS -> results.albums.size
        GENRES -> results.genres.size
        FOLDERS -> results.folders.size
        LYRICS -> results.lyrics.size
    }
}

@Composable
fun LibrarySearchScreen(
    state: LibrarySearchUiState,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
    onSong: (com.theemrock.smoothmedia.library.LibrarySong) -> Unit,
    onBack: () -> Unit,
    onLyricSong: (com.theemrock.smoothmedia.library.LibrarySong) -> Unit = onSong,
) {
    val searched = state.query.isNotBlank()
    var tab by rememberSaveable { mutableStateOf(SearchTab.SONGS) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Text("‹", color = Color.White, fontSize = 30.sp) }
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Kütüphanede ara") },
                trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = onClear) { Text("×") } },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        ) {
            SearchTab.entries.forEach { entry ->
                val selected = entry == tab
                val label = if (searched && state.loaded) "${entry.label} (${entry.count(state.results)})" else entry.label
                Text(
                    label,
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) SmoothMediaColors.Accent else SmoothMediaColors.SurfaceVariant)
                        .clickable { tab = entry }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        when {
            state.loading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            state.errorMessage != null -> Text(state.errorMessage, modifier = Modifier.padding(24.dp), color = Color(0xFFFF8A80))
            searched && state.loaded && tab.count(state.results) == 0 ->
                Text("Sonuç yok", modifier = Modifier.padding(24.dp), color = Color(0xFFAAB9C2))
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tab) {
                    SearchTab.SONGS -> items(state.results.songs, key = { it.trackId }) { song ->
                        Column(Modifier.fillMaxWidth().alpha(if (song.isAccessible) 1f else 0.55f).clickable { onSong(song) }.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(song.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${song.artist} · ${song.album}", color = Color(0xFFAAB9C2), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!song.isAccessible) Text(song.accessibilityDescription, color = Color(0xFFFFB4AB))
                        }
                    }
                    SearchTab.ARTISTS -> collectionItems(state.results.artists)
                    SearchTab.ALBUMS -> collectionItems(state.results.albums)
                    SearchTab.GENRES -> collectionItems(state.results.genres)
                    SearchTab.FOLDERS -> collectionItems(state.results.folders)
                    SearchTab.LYRICS -> items(state.results.lyrics, key = { "lyrics-${it.song.trackId}" }) { hit ->
                        Column(Modifier.fillMaxWidth().clickable { onLyricSong(hit.song) }.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(hit.song.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(hit.song.artist, color = Color(0xFFAAB9C2), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(hit.snippet, color = Color(0xFF19C3E6), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.collectionItems(rows: List<LibraryCollectionRow>) {
    items(rows.map { it.title }) { value ->
        Text(value, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
}
