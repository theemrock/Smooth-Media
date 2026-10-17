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

private val SettingsInk = SmoothMediaColors.Background
private val SettingsMuted = SmoothMediaColors.TextMuted
private val SettingsAccent = SmoothMediaColors.Accent

@Composable
fun SettingsScreen(
    onMenu: () -> Unit,
    onHome: () -> Unit,
    onLibraryManagement: () -> Unit,
) {
    SettingsScaffold(onMenu = onMenu, onHome = onHome) {
        SettingsRow(
            icon = "▦",
            title = "Kütüphane Yönetimi",
            subtitle = "Müzik kaynakları ve tarama ayarları",
            onClick = onLibraryManagement,
        )
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
