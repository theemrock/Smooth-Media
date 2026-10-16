package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.theemrock.smoothmedia.eq.AdaptiveEqState
import com.theemrock.smoothmedia.eq.EqualizerBand

@Composable
fun EqualizerScreen(
    adaptiveState: AdaptiveEqState,
    manualLevels: Map<Short, Int>,
    bands: List<EqualizerBand>,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onReset: () -> Unit,
    onManualLevelChanged: (Short, Int) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Button(onClick = onBack) { Text("Back") }
            Text("EQUALIZER", color = Color(0xFF19C3E6), modifier = Modifier.padding(12.dp))
        }
        Text("Adaptive EQ", color = Color.White)
        Text(
            if (adaptiveState.isAvailable) "Adaptive measured state active" else "Adaptive EQ is unavailable until measured audio input exists.",
            color = Color(0xFFAAB9C2),
        )
        Text("Manual EQ", color = Color.White)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onEnabledChanged(!enabled) }) { Text(if (enabled) "Enabled" else "Bypass") }
            Button(onClick = onReset, enabled = bands.isNotEmpty()) { Text("Flat") }
        }
        if (bands.isEmpty()) {
            Text("Manual bands are not exposed by the current audio session.", color = Color(0xFFAAB9C2))
        } else {
            bands.forEach { band ->
                val level = manualLevels[band.id] ?: band.levelMb
                Text("${band.centerFrequencyHz} Hz", color = Color(0xFFAAB9C2))
                Slider(
                    value = level.toFloat(),
                    onValueChange = { onManualLevelChanged(band.id, it.toInt()) },
                    valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat(),
                )
            }
        }
    }
}
