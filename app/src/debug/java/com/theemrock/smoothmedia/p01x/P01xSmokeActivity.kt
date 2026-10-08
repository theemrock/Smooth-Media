package com.theemrock.smoothmedia.p01x

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.theemrock.smoothmedia.R
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class P01xSmokeActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var state by mutableStateOf<SmokeUiState>(SmokeUiState.PermissionRequired)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            P01xTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) loadLibrary() else state = SmokeUiState.PermissionRequired
                }
                P01xScreen(
                    state = state,
                    onGrantPermission = { permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO) },
                )
            }
        }

        if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            loadLibrary()
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun loadLibrary() {
        if (state == SmokeUiState.Loading) return
        state = SmokeUiState.Loading
        worker.execute {
            try {
                val discovered = MediaStoreMusicDiscovery(contentResolver).discover()
                runOnUiThread { state = SmokeUiState.Loaded(discovered) }
            } catch (error: RuntimeException) {
                Log.e(TAG, "P01X discovery failed", error)
                runOnUiThread { state = SmokeUiState.Failed }
            }
        }
    }

    private companion object {
        const val TAG = "SmoothMediaP01X"
    }
}

private sealed interface SmokeUiState {
    data object PermissionRequired : SmokeUiState
    data object Loading : SmokeUiState
    data class Loaded(val files: List<DiscoveredAudio>) : SmokeUiState
    data object Failed : SmokeUiState
}

@Composable
private fun P01xScreen(
    state: SmokeUiState,
    onGrantPermission: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.p01x_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.p01x_developer_only),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        when (state) {
            SmokeUiState.PermissionRequired -> PermissionRequired(onGrantPermission)
            SmokeUiState.Loading -> Text(stringResource(R.string.p01x_loading))
            SmokeUiState.Failed -> Text(stringResource(R.string.p01x_load_failed))
            is SmokeUiState.Loaded -> LibraryList(state.files)
        }
    }
}

@Composable
private fun PermissionRequired(onGrantPermission: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.p01x_permission_required))
        Button(onClick = onGrantPermission) {
            Text(stringResource(R.string.p01x_grant_permission))
        }
    }
}

@Composable
private fun LibraryList(files: List<DiscoveredAudio>) {
    if (files.isEmpty()) {
        Text(stringResource(R.string.p01x_empty))
        return
    }
    val mp3Count = files.count { it.formatLabel == "MP3" }
    val flacCount = files.count { it.formatLabel == "FLAC" }
    Text(stringResource(R.string.p01x_file_count, files.size))
    Text(stringResource(R.string.p01x_format_count, mp3Count, flacCount))
    Spacer(modifier = Modifier.height(8.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(files, key = { it.contentUri.toString() }) { audio ->
            AudioProbeCard(audio)
        }
    }
}

@Composable
private fun AudioProbeCard(audio: DiscoveredAudio) {
    val context = LocalContext.current
    val reader = remember(context) { AndroidAudioMetadataReader(context.applicationContext) }
    val metadata by produceState<AudioMetadataProbe?>(null, audio.contentUri) {
        value = withContext(Dispatchers.IO) { reader.read(audio) }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Artwork(metadata, audio.displayName)
            Column(modifier = Modifier.weight(1f)) {
                Text(audio.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.p01x_file_location, audio.formatLabel, audio.relativePath),
                    style = MaterialTheme.typography.labelSmall,
                )
                if (metadata == null) {
                    Text(stringResource(R.string.p01x_metadata_loading))
                } else {
                    MetadataDetails(requireNotNull(metadata))
                }
            }
        }
    }
}

@Composable
private fun Artwork(metadata: AudioMetadataProbe?, displayName: String) {
    val bytes = metadata?.artwork?.bytes
    val bitmap = remember(bytes) {
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = stringResource(R.string.p01x_artwork_description, displayName),
            modifier = Modifier.size(72.dp),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                metadata?.artwork?.status?.displayText()
                    ?: stringResource(R.string.p01x_placeholder),
            )
        }
    }
}

@Composable
private fun MetadataDetails(metadata: AudioMetadataProbe) {
    MetadataLine(R.string.p01x_title_label, metadata.title.value, metadata.title.status)
    MetadataLine(R.string.p01x_artist_label, metadata.artist.value, metadata.artist.status)
    MetadataLine(R.string.p01x_album_label, metadata.album.value, metadata.album.status)
    MetadataLine(R.string.p01x_album_artist_label, metadata.albumArtist.value, metadata.albumArtist.status)
    MetadataLine(R.string.p01x_year_label, metadata.year.value?.toString(), metadata.year.status)
    MetadataLine(R.string.p01x_track_label, metadata.trackNumber.value, metadata.trackNumber.status)
    MetadataLine(R.string.p01x_disc_label, metadata.discNumber.value, metadata.discNumber.status)
    val genre = metadata.genres.values.joinToString().ifBlank { null }
    MetadataLine(R.string.p01x_genre_label, genre, metadata.genres.status)
    val duration = metadata.durationMs.value?.let {
        stringResource(R.string.p01x_duration_value, it / 1_000)
    }
    MetadataLine(R.string.p01x_duration_label, duration, metadata.durationMs.status)
    val bitrate = metadata.bitrate.value?.let {
        stringResource(R.string.p01x_bitrate_value, it / 1_000)
    }
    MetadataLine(R.string.p01x_bitrate_label, bitrate, metadata.bitrate.status)
    val sampleRate = metadata.sampleRate.value?.let {
        stringResource(R.string.p01x_sample_rate_value, it)
    }
    MetadataLine(R.string.p01x_sample_rate_label, sampleRate, metadata.sampleRate.status)
    MetadataLine(
        R.string.p01x_channels_label,
        metadata.channels.value?.toString(),
        metadata.channels.status,
    )
    MetadataLine(R.string.p01x_artwork_label, null, metadata.artwork.status)
}

@Composable
private fun MetadataLine(labelRes: Int, value: String?, status: ProbeStatus) {
    val shownValue = when {
        value != null && status == ProbeStatus.AVAILABLE -> value
        value != null -> "$value (${status.displayText()})"
        else -> status.displayText()
    }
    Text(
        stringResource(R.string.p01x_value_format, stringResource(labelRes), shownValue),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ProbeStatus.displayText(): String = when (this) {
    ProbeStatus.AVAILABLE -> stringResource(R.string.p01x_status_available)
    ProbeStatus.MISSING -> stringResource(R.string.p01x_status_missing)
    ProbeStatus.INCOMPLETE -> stringResource(R.string.p01x_status_incomplete)
    ProbeStatus.UNSUPPORTED -> stringResource(R.string.p01x_status_unsupported)
}

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

@Composable
private fun P01xTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
