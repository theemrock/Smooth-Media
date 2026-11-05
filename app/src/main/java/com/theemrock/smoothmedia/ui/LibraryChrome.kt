package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theemrock.smoothmedia.library.LibraryCollectionType

/** Counts shown on the category cards; null renders as a dash until the dashboard data has loaded. */
data class LibraryCategoryCounts(
    val tracks: Int? = null,
    val albums: Int? = null,
    val artists: Int? = null,
    val genres: Int? = null,
    val folders: Int? = null,
    val playlists: Int? = null,
)

enum class LibraryChromeTab { NONE, SONGS, ALBUMS, ARTISTS, GENRES, PLAYLISTS, FOLDERS }

fun LibraryCollectionType.chromeTab(): LibraryChromeTab = when (this) {
    LibraryCollectionType.ALBUMS -> LibraryChromeTab.ALBUMS
    LibraryCollectionType.ARTISTS -> LibraryChromeTab.ARTISTS
    LibraryCollectionType.GENRES -> LibraryChromeTab.GENRES
    LibraryCollectionType.PLAYLISTS -> LibraryChromeTab.PLAYLISTS
    LibraryCollectionType.FOLDERS -> LibraryChromeTab.FOLDERS
}

/** Same top bar and category cards as the dashboard, shared by every library screen. */
@Composable
fun LibraryChrome(
    counts: LibraryCategoryCounts,
    selected: LibraryChromeTab,
    onSearch: () -> Unit,
    onDashboard: () -> Unit,
    onSongs: () -> Unit,
    onCollection: (LibraryCollectionType) -> Unit,
    onSources: () -> Unit,
    onSettings: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column {
        DashboardTopBar(
            menuExpanded = menuExpanded,
            onMenuChange = { menuExpanded = it },
            onSearch = onSearch,
            onSongs = onSongs,
            onCollection = onCollection,
            onSources = onSources,
            onSettings = onSettings,
            onDashboard = onDashboard,
        )
        CategoryStrip(counts, selected, onSongs, onCollection)
        Spacer(Modifier.height(8.dp))
    }
}

/** Albums, artists and genres can be multi-selected; playlists and folders cannot. */
fun LibraryCollectionType.supportsSelection(): Boolean =
    this != LibraryCollectionType.FOLDERS && this != LibraryCollectionType.PLAYLISTS
