package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.theemrock.smoothmedia.lyrics.LyricsBulkFetcher
import com.theemrock.smoothmedia.lyrics.LyricsBulkMode

private val SettingsInk = SmoothMediaColors.Background
private val SettingsMuted = SmoothMediaColors.TextMuted
private val SettingsAccent = SmoothMediaColors.Accent

@Composable
fun SettingsScreen(
    onMenu: () -> Unit,
    onHome: () -> Unit,
    onLibraryManagement: () -> Unit,
    onLyricsBulk: () -> Unit = {},
    onRefreshId3: () -> Unit = {},
    id3RefreshRunning: Boolean = false,
    id3RefreshStatus: String? = null,
    eqScanSubtitle: String = "",
    onEqScan: () -> Unit = {},
) {
    SettingsScaffold(onMenu = onMenu, onHome = onHome) {
        SettingsRow(
            icon = "≋",
            title = "EQ analizi (Auto EQ)",
            subtitle = eqScanSubtitle.ifEmpty { "Şarkıları tarayıp Auto EQ için bant profili çıkarır" },
            onClick = onEqScan,
        )
        SettingsRow(
            icon = "▦",
            title = "Kütüphane Yönetimi",
            subtitle = "Müzik kaynakları ve tarama ayarları",
            onClick = onLibraryManagement,
        )
        SettingsRow(
            icon = "♬",
            title = "Şarkı sözlerini toplu çek",
            subtitle = "İnternet varken sözsüz parçalar için lrclib'den söz indirir",
            onClick = onLyricsBulk,
        )
        SettingsRow(
            icon = "↻",
            title = "ID3 etiketlerini yenile",
            subtitle = id3RefreshStatus
                ?: "Tüm şarkıların etiketlerini dosyalardan yeniden okur (bozuk karakterleri düzeltir)",
            onClick = { if (!id3RefreshRunning) onRefreshId3() },
        )
    }
}

/** Filter buttons above the bulk lyrics log. */
enum class LyricsLogFilter(val label: String) {
    ALL("Tümü"),
    DOWNLOADED("İndirildi"),
    NOT_FOUND("Bulunamadı"),
    ERROR("Hata");

    fun matches(entry: LyricsBulkFetcher.LogEntry): Boolean = when (this) {
        ALL -> true
        DOWNLOADED -> entry.result.startsWith("indirildi")
        NOT_FOUND -> entry.result == "bulunamadı"
        ERROR -> entry.result.startsWith("hata")
    }
}

/** Full page for the bulk lyrics download: start/stop, progress summary, and a scrollable per-track log. */
@Composable
fun LyricsBulkScreen(
    running: Boolean,
    status: String?,
    progress: String?,
    log: List<LyricsBulkFetcher.LogEntry>,
    onStart: (LyricsBulkMode) -> Unit,
    onStop: () -> Unit,
    onMenu: () -> Unit,
    onHome: () -> Unit,
) {
    var logFilter by remember { mutableStateOf(LyricsLogFilter.ALL) }
    Surface(color = SettingsInk, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryNavigationBar(onMenu = onMenu, onDashboard = onHome, onSettings = onMenu)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Şarkı sözlerini toplu çek", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "İnternet varken sözsüz parçalar için lrclib'den söz indirir. Uygulama açık kalmalı.",
                    color = SettingsMuted,
                    fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    if (running) {
                        Text("Durdur", color = SmoothMediaColors.Error, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onStop))
                    } else {
                        Text("Yeni parçalar", color = SettingsAccent, fontSize = 16.sp, modifier = Modifier.clickable { onStart(LyricsBulkMode.NEW) })
                        Text("Bulunamayanlar", color = SettingsAccent, fontSize = 16.sp, modifier = Modifier.clickable { onStart(LyricsBulkMode.NOT_FOUND) })
                        Text("Hatalar", color = SettingsAccent, fontSize = 16.sp, modifier = Modifier.clickable { onStart(LyricsBulkMode.ERROR) })
                    }
                }
                progress?.let { Text(it, color = Color.White, fontSize = 15.sp) }
                status?.let { Text(it, color = SettingsMuted, fontSize = 13.sp) }
                Text("Son işlenenler (yeniden eskiye)", color = SettingsAccent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    LyricsLogFilter.entries.forEach { filter ->
                        val selected = filter == logFilter
                        Text(
                            filter.label,
                            color = if (selected) Color.Black else Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) SettingsAccent else SmoothMediaColors.SurfaceVariant)
                                .clickable { logFilter = filter }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                val visible = log.filter { logFilter.matches(it) }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(visible) { entry ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                "${entry.title} — ${entry.artist}",
                                color = Color.White,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                entry.result,
                                color = if (entry.result.startsWith("hata")) SmoothMediaColors.Error else SettingsMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryManagementScreen(
    onMenu: () -> Unit,
    onHome: () -> Unit,
    onMusicSources: () -> Unit,
) {
    SettingsScaffold(onMenu = onMenu, onHome = onHome) {
        SettingsRow(
            icon = "♫",
            title = "Müzik Kaynakları",
            subtitle = "Kullanıcı tarafından seçilen klasörler ve tarama durumu",
            onClick = onMusicSources,
        )
    }
}

@Composable
private fun SettingsScaffold(
    onMenu: () -> Unit,
    onHome: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(color = SettingsInk, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryNavigationBar(onMenu = onMenu, onDashboard = onHome, onSettings = onMenu)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = { content() },
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(icon, color = SettingsAccent, fontSize = 22.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(subtitle, color = SettingsMuted, fontSize = 13.sp)
        }
        Text("›", color = SettingsMuted, fontSize = 24.sp)
    }
}
