package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class EqScanListRow(val title: String, val artist: String)

private val turkish = Locale("tr", "TR")

/** Song EQ scan page: start/stop, progress, and the scanned songs grouped by first letter. */
@Composable
fun EqScanScreen(
    running: Boolean,
    progress: String,
    rows: List<EqScanListRow>,
    onToggle: () -> Unit,
    onMenu: () -> Unit,
    onHome: () -> Unit,
) {
    val grouped = remember(rows) {
        rows.groupBy { row -> row.title.firstOrNull()?.takeIf { it.isLetter() }?.uppercase(turkish) ?: "#" }
    }
    Surface(color = SmoothMediaColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryNavigationBar(onMenu = onMenu, onDashboard = onHome, onSettings = onMenu)
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("EQ analizi (Auto EQ)", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Şarkıları tarayıp Auto EQ için bant profili çıkarır. Durdurunca kaldığı yerden devam eder.",
                    color = SmoothMediaColors.TextMuted,
                    fontSize = 13.sp,
                )
                Text(
                    if (running) "Durdur" else "Taramayı başlat",
                    color = if (running) SmoothMediaColors.Error else SmoothMediaColors.Accent,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 6.dp).clickable(onClick = onToggle),
                )
                Text(progress, color = Color.White, fontSize = 15.sp)
                Text("Taranan şarkılar (A-Z)", color = SmoothMediaColors.Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    grouped.forEach { (letter, songs) ->
                        item(key = "header-$letter") {
                            Text(letter, color = SmoothMediaColors.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        }
                        items(songs) { song ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(song.title.ifBlank { "(başlıksız)" }, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (song.artist.isNotBlank()) {
                                    Text(song.artist, color = SmoothMediaColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
