package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.library.LibrarySearchUiState

@Composable
fun LibrarySearchScreen(
    state: LibrarySearchUiState,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
    onSong: (com.theemrock.smoothmedia.library.LibrarySong) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Text("‹", color = Color.White, fontSize = 30.sp) }
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Search library") },
                trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = onClear) { Text("×") } },
            )
        }
        when {
            state.loading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            state.errorMessage != null -> Text(state.errorMessage, modifier = Modifier.padding(24.dp), color = Color(0xFFFF8A80))
            state.query.isNotBlank() && state.loaded && state.results.isEmpty -> Text("No results", modifier = Modifier.padding(24.dp), color = Color(0xFFAAB9C2))
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { SearchHeading("Songs", state.results.songs.size) }
                items(state.results.songs, key = { it.trackId }) { song ->
                    Column(Modifier.fillMaxWidth().alpha(if (song.isAccessible) 1f else 0.55f).clickable { onSong(song) }.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(song.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${song.artist} · ${song.album}", color = Color(0xFFAAB9C2), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!song.isAccessible) Text(song.accessibilityDescription, color = Color(0xFFFFB4AB))
                    }
                }
                searchCollections("Artists", state.results.artists.map { it.title })
                searchCollections("Albums", state.results.albums.map { it.title })
                searchCollections("Genres", state.results.genres.map { it.title })
                searchCollections("Folders", state.results.folders.map { it.title })
            }
        }
    }
}

@Composable
private fun SearchHeading(label: String, count: Int) {
    Text("$label ($count)", color = Color(0xFF19C3E6), modifier = Modifier.padding(8.dp))
}

private fun LazyListScope.searchCollections(label: String, values: List<String>) {
    if (values.isEmpty()) return
    item { SearchHeading(label, values.size) }
    items(values) { value -> Text(value, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
}
