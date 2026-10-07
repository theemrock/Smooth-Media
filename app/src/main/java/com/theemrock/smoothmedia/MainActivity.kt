package com.theemrock.smoothmedia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF19C3E6),
    background = Color(0xFF090B0D),
    surface = Color(0xFF111418),
    onPrimary = Color(0xFF090B0D),
    onBackground = Color(0xFFF4F6F8),
    onSurface = Color(0xFFF4F6F8),
    outline = Color(0xFFD8DEE3),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF19C3E6),
    background = Color(0xFFF5F7F8),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF090B0D),
    onBackground = Color(0xFF111418),
    onSurface = Color(0xFF111418),
    outline = Color(0xFFD8DEE3),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmoothMediaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SmoothMediaShell()
                }
            }
        }
    }
}

@Composable
private fun SmoothMediaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme,
        content = content,
    )
}

@Composable
private fun SmoothMediaShell() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Smooth Media",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Phase 01",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
