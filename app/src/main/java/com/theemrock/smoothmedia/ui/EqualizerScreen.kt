package com.theemrock.smoothmedia.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.theemrock.smoothmedia.eq.AdaptiveEqState
import com.theemrock.smoothmedia.eq.EqMode
import com.theemrock.smoothmedia.eq.EqualizerBand
import com.theemrock.smoothmedia.eq.ParametricEq

@Composable
fun EqualizerScreen(
    adaptiveState: AdaptiveEqState,
    manualLevels: Map<Short, Int>,
    bands: List<EqualizerBand>,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onReset: () -> Unit,
    onManualLevelChanged: (Short, Int) -> Unit,
    songEqMode: EqMode,
    onSongEqModeChanged: (EqMode) -> Unit,
    savedAutoEqText: String?,
    onApplyAutoEq: (String?) -> Unit,
    onBack: () -> Unit,
) {
    var autoEqInput by rememberSaveable { mutableStateOf(savedAutoEqText.orEmpty()) }
    val autoEqProfile = ParametricEq.parseAutoEq(autoEqInput)
    // Follow the physical sensor on this screen only, even when the system rotation lock is on.
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        onDispose { activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Button(onClick = onBack) { Text("Back") }
            Text("EQUALIZER", color = Color(0xFF19C3E6), modifier = Modifier.padding(12.dp))
        }
        Text("Song EQ mode", color = Color.White)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onSongEqModeChanged(EqMode.AUTO) }, enabled = songEqMode != EqMode.AUTO) { Text("Auto (dynamic)") }
            Button(onClick = { onSongEqModeChanged(EqMode.MANUAL) }, enabled = songEqMode != EqMode.MANUAL) { Text("Manual") }
        }
        Text(
            if (songEqMode == EqMode.AUTO) "Scanned songs follow their own dynamic EQ; the headphone profile is off for them. Applies from the next song." else "Headphone profile and manual bands.",
            color = Color(0xFFAAB9C2),
        )
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
            Text("Tilt the phone sideways for the full 24-band view. Double-tap a band to zero it.", color = Color(0xFFAAB9C2))
            BandSliders(bands, manualLevels, onManualLevelChanged)
        }
        Text("AutoEQ", color = Color.White)
        OutlinedTextField(
            value = autoEqInput,
            onValueChange = { autoEqInput = it },
            label = { Text("Paste AutoEQ profile") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Text(
            "${autoEqProfile.bands.size} bands, preamp ${autoEqProfile.preampDb} dB",
            color = Color(0xFFAAB9C2),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onApplyAutoEq(autoEqInput.takeIf { it.isNotBlank() }) }) { Text("Apply") }
            Button(onClick = {
                autoEqInput = ""
                onApplyAutoEq(null)
            }) { Text("Clear") }
        }
    }
}

@Composable
private fun BandSliders(bands: List<EqualizerBand>, levels: Map<Short, Int>, onChange: (Short, Int) -> Unit) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perBand = if (landscape) maxOf(maxWidth / bands.size, 28.dp) else 44.dp
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            bands.forEach { band ->
                val level = levels[band.id] ?: band.levelMb
                val db = level / 100
                Column(Modifier.width(perBand), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (db > 0) "+$db" else "$db", color = Color(0xFFAAB9C2), fontSize = 9.sp)
                    VerticalBandSlider(
                        level = level,
                        min = band.minLevelMb,
                        max = band.maxLevelMb,
                        onChange = { onChange(band.id, it) },
                        modifier = Modifier.width(perBand).height(if (landscape) 150.dp else 200.dp),
                    )
                    Text(frequencyLabel(band.centerFrequencyHz), color = Color(0xFFAAB9C2), fontSize = 9.sp)
                }
            }
        }
    }
}

/** Drag or tap sets the level in 0.5 dB steps; double-tap zeroes it. Custom drawing keeps touch mapping exact. */
@Composable
private fun VerticalBandSlider(level: Int, min: Int, max: Int, onChange: (Int) -> Unit, modifier: Modifier) {
    val current by rememberUpdatedState(onChange)
    Canvas(
        modifier
            .pointerInput(min, max) {
                detectTapGestures(onDoubleTap = { current(0) }, onTap = { current(levelAt(it.y, size.height, min, max)) })
            }
            .pointerInput(min, max) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    current(levelAt(change.position.y, size.height, min, max))
                }
            },
    ) {
        fun yFor(value: Int) = (max - value).toFloat() / (max - min) * size.height
        val x = size.width / 2
        val zeroY = yFor(0)
        val thumbY = yFor(level)
        drawLine(Color(0xFF3A4A54), Offset(x, 0f), Offset(x, size.height), strokeWidth = 4f)
        drawLine(Color(0xFF6B7C86), Offset(x - 10f, zeroY), Offset(x + 10f, zeroY), strokeWidth = 2f)
        drawLine(Color(0xFF19C3E6), Offset(x, zeroY), Offset(x, thumbY), strokeWidth = 6f)
        drawCircle(Color(0xFF19C3E6), radius = 10f, center = Offset(x, thumbY))
    }
}

private fun levelAt(y: Float, height: Int, min: Int, max: Int): Int {
    val raw = max - (y / height).coerceIn(0f, 1f) * (max - min)
    return ((raw / 50).roundToInt() * 50).coerceIn(min, max)
}

private fun frequencyLabel(hz: Int): String = if (hz >= 1000) "${(hz / 100) / 10.0}k".replace(".0k", "k") else "$hz"
