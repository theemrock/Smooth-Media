package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theemrock.smoothmedia.library.LibrarySong

@Composable
fun LyricsScreen(song: LibrarySong, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Text("‹", color = Color.White, fontSize = 30.sp) }
            Text("Sözler", color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        }
        Text(song.title, color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center)
        Text(song.artist + " · " + song.album, color = Color(0xFFAAB9C2), textAlign = TextAlign.Center)
        Text(
            "Bu parça için yerel veya senkronize söz verisi bulunmuyor.",
            color = Color(0xFFAAB9C2),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 64.dp),
        )
    }
}
