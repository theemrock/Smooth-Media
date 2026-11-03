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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.library.FolderTreeRow
import com.theemrock.smoothmedia.library.LibraryCollectionType

private val TreeInk = Color(0xFF080B0D)
private val TreeMuted = Color(0xFFAAB9C2)
private val TreeCyan = Color(0xFF19C3E6)
private val TreeLine = Color(0xFF26343B)

/**
 * Folder browser as a tree: each real SAF folder is one line, indented by depth.
 * Tapping a folder with subfolders expands or collapses it; "Şarkılar" opens that folder's songs.
 */
@Composable
fun FolderTreeScreen(
    rows: List<FolderTreeRow>,
    loaded: Boolean,
    onToggle: (FolderTreeRow) -> Unit,
    onOpen: (FolderTreeRow) -> Unit,
    onSongs: () -> Unit,
    onCollection: (LibraryCollectionType) -> Unit,
    onSearch: () -> Unit,
    onMenu: () -> Unit,
    onDashboard: () -> Unit,
    onSources: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(TreeInk)) {
        LibraryNavigationBar(
            onMenu = onMenu,
            onSearch = onSearch,
            onDashboard = onDashboard,
            onSongs = onSongs,
            onCollection = onCollection,
            onSources = onSources,
            onSettings = onSettings,
        )
        Text(
            "Klasörler",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
        Text(
            if (loaded) "${rows.count { it.depth == 0 }} kök klasör" else "Yükleniyor…",
            color = TreeMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        LibrarySectionTabs(
            selected = LibraryCollectionType.FOLDERS,
            onSongs = onSongs,
            onCollection = onCollection,
        )
        if (loaded && rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Henüz klasör yok", color = TreeMuted, fontSize = 16.sp)
            }
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(rows, key = { it.node.key }) { row ->
                FolderTreeLine(row, onToggle = { onToggle(row) }, onOpen = { onOpen(row) })
            }
        }
    }
}

@Composable
private fun FolderTreeLine(row: FolderTreeRow, onToggle: () -> Unit, onOpen: () -> Unit) {
    val node = row.node
    val indent = (row.depth * 18).dp
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = { if (node.hasChildren) onToggle() else onOpen() })
            .padding(start = indent, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                node.hasChildren && row.expanded -> Text("▾", color = TreeCyan, fontSize = 22.sp)
                node.hasChildren -> Text("▸", color = TreeCyan, fontSize = 22.sp)
                else -> Text("•", color = TreeLine, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                node.name,
                color = if (row.depth == 0) Color.White else Color(0xFFE6EDF1),
                fontSize = 16.sp,
                fontWeight = if (row.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                trackLabel(node.trackCount),
                color = TreeMuted,
                fontSize = 12.sp,
            )
        }
        TextButton(onClick = onOpen) {
            Text("Şarkılar", color = TreeCyan, fontSize = 13.sp)
        }
    }
}

private fun trackLabel(count: Int): String = when (count) {
    0 -> "şarkı yok"
    1 -> "1 şarkı"
    else -> "$count şarkı"
}
