package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.library.LibrarySong
import com.theemrock.smoothmedia.lyrics.CachedLyrics
import com.theemrock.smoothmedia.lyrics.LrcParser
import com.theemrock.smoothmedia.lyrics.LyricsMode

@Composable
fun LyricsScreen(
    song: LibrarySong,
    onBack: () -> Unit,
    cachedLyrics: CachedLyrics? = null,
    positionMs: Long = 0L,
    onSeek: (Long) -> Unit = {},
) {
    var synced by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().background(SmoothMediaColors.Background).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹", color = Color.White, fontSize = 30.sp) }
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${song.artist} · ${song.album}", color = SmoothMediaColors.TextMuted, maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { synced = false }) { Text("Plain", color = if (!synced) SmoothMediaColors.Accent else SmoothMediaColors.TextMuted) }
            TextButton(onClick = { synced = true }) { Text("Synced", color = if (synced) SmoothMediaColors.Accent else SmoothMediaColors.TextMuted) }
        }
        val hasSynced = cachedLyrics?.mode == LyricsMode.SYNCED
        if (cachedLyrics == null || (synced && !hasSynced)) {
            Text(
                if (synced) "Bu parça için senkronize söz verisi bulunmuyor." else "Bu parça için yerel söz verisi bulunmuyor.",
                color = SmoothMediaColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 64.dp),
            )
        } else if (synced) {
            val parsed = LrcParser.parse(cachedLyrics.text)
            val active = parsed.activeLineAt(positionMs)
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(parsed.lines) { index, line ->
                    Text(
                        line.text,
                        color = if (index == active) SmoothMediaColors.Accent else Color.White,
                        fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clickable { onSeek(parsed.seekTargetForLine(index) ?: 0L) }.padding(vertical = 10.dp),
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(cachedLyrics.text.lines()) { _, line ->
                    Text(line, color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}
