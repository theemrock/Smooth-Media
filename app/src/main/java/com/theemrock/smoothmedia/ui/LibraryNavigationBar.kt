package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.library.LibraryCollectionType

private val NavigationMuted = Color(0xFFAAB9C2)
private val NavigationCyan = Color(0xFF19C3E6)

@Composable
fun LibraryNavigationBar(
    onMenu: () -> Unit = {},
    onSearch: () -> Unit = {},
    onDashboard: () -> Unit = {},
    onSongs: () -> Unit = {},
    onCollection: (LibraryCollectionType) -> Unit = {},
    onSources: () -> Unit = {},
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
            DropdownMenuItem(text = { Text("Kütüphane") }, onClick = { menuExpanded = false; onDashboard() })
            DropdownMenuItem(text = { Text("Tüm şarkılar") }, onClick = { menuExpanded = false; onSongs() })
            DropdownMenuItem(text = { Text("Albümler") }, onClick = { menuExpanded = false; onCollection(LibraryCollectionType.ALBUMS) })
            DropdownMenuItem(text = { Text("Sanatçılar") }, onClick = { menuExpanded = false; onCollection(LibraryCollectionType.ARTISTS) })
            DropdownMenuItem(text = { Text("Türler") }, onClick = { menuExpanded = false; onCollection(LibraryCollectionType.GENRES) })
            DropdownMenuItem(text = { Text("Klasörler") }, onClick = { menuExpanded = false; onCollection(LibraryCollectionType.FOLDERS) })
            DropdownMenuItem(text = { Text("Kaynaklar") }, onClick = { menuExpanded = false; onSources() })
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
