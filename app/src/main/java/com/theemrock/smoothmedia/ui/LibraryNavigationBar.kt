package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.library.LibraryCollectionType

private val NavigationMuted = Color(0xFFAAB9C2)
private val NavigationCyan = Color(0xFF19C3E6)

internal enum class MainMenuDestination { DASHBOARD, SONGS, ALBUMS, ARTISTS, GENRES, FOLDERS, SETTINGS }

internal data class MainMenuItemSpec(
    val destination: MainMenuDestination,
    val label: String,
    val icon: String,
)

internal val mainMenuItems = listOf(
    MainMenuItemSpec(MainMenuDestination.DASHBOARD, "Kütüphane", "⌂"),
    MainMenuItemSpec(MainMenuDestination.SONGS, "Tüm şarkılar", "♫"),
    MainMenuItemSpec(MainMenuDestination.ALBUMS, "Albümler", "▦"),
    MainMenuItemSpec(MainMenuDestination.ARTISTS, "Sanatçılar", "●"),
    MainMenuItemSpec(MainMenuDestination.GENRES, "Türler", "◇"),
    MainMenuItemSpec(MainMenuDestination.FOLDERS, "Klasörler", "■"),
    MainMenuItemSpec(MainMenuDestination.SETTINGS, "Ayarlar", "⚙"),
)

internal enum class SettingsDestination {
    SETTINGS,
    LIBRARY_MANAGEMENT,
    MUSIC_SOURCES;

    val musicSourcesDestination: SettingsDestination
        get() = MUSIC_SOURCES
}

@Composable
fun LibraryNavigationBar(
    onMenu: () -> Unit = {},
    onSearch: () -> Unit = {},
    onDashboard: () -> Unit = {},
    onSongs: () -> Unit = {},
    onCollection: (LibraryCollectionType) -> Unit = {},
    onSources: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = { menuExpanded = !menuExpanded; onMenu() }) {
            Text("☰", color = Color.White, fontSize = 23.sp)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            mainMenuItems.forEachIndexed { index, item ->
                if (index == mainMenuItems.lastIndex) {
                    HorizontalDivider(color = NavigationMuted.copy(alpha = .35f))
                }
                DropdownMenuItem(
                    leadingIcon = {
                        Text(item.icon, color = if (item.destination == MainMenuDestination.SETTINGS) NavigationCyan else Color.White, modifier = Modifier.width(28.dp))
                    },
                    text = { Text(item.label) },
                    onClick = {
                        menuExpanded = false
                        when (item.destination) {
                            MainMenuDestination.DASHBOARD -> onDashboard()
                            MainMenuDestination.SONGS -> onSongs()
                            MainMenuDestination.ALBUMS -> onCollection(LibraryCollectionType.ALBUMS)
                            MainMenuDestination.ARTISTS -> onCollection(LibraryCollectionType.ARTISTS)
                            MainMenuDestination.GENRES -> onCollection(LibraryCollectionType.GENRES)
                            MainMenuDestination.FOLDERS -> onCollection(LibraryCollectionType.FOLDERS)
                            MainMenuDestination.SETTINGS -> onSettings()
                        }
                    },
                )
            }
        }
        Row(
            modifier = Modifier.weight(1f).clickable(onClick = onSearch).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⌕", color = Color.White, fontSize = 24.sp)
            Text("  Kütüphanede ara…", color = NavigationMuted, fontSize = 15.sp, maxLines = 1)
        }
        Spacer(modifier = Modifier.padding(2.dp))
        IconButton(onClick = onDashboard) {
            Text("⌂", color = NavigationCyan, fontSize = 24.sp)
        }
    }
}
