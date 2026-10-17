package com.theemrock.smoothmedia.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theemrock.smoothmedia.R
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.scan.ManualScanProgress
import com.theemrock.smoothmedia.catalog.scan.ScanPhase
import com.theemrock.smoothmedia.metadata.MetadataBatchProgress
import com.theemrock.smoothmedia.metadata.MetadataSessionProgress
import com.theemrock.smoothmedia.metadata.MetadataSessionStatus
import com.theemrock.smoothmedia.source.SourceRefreshResult
import com.theemrock.smoothmedia.source.SourceRegistrationResult
import com.theemrock.smoothmedia.source.SourceRemovalResult

internal fun sourceDisplayName(source: LibrarySourceEntity): String =
    source.volumeName?.trim()?.takeIf { it.isNotEmpty() }
        ?: "SAF kaynağı · ${source.sourceId.takeLast(6)}"

@Composable
fun SourceManagementScreen(
    sources: List<LibrarySourceEntity>,
    statusMessage: String?,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
    onRemove: (String) -> Unit,
    scanningSourceId: String? = null,
    onScan: (String) -> Unit = {},
    scanPhase: ScanPhase = ScanPhase.IDLE,
    scanProgress: ManualScanProgress? = null,
    scanExecutionAttached: Boolean = false,
    onPauseScan: () -> Unit = {},
    onResumeScan: () -> Unit = {},
    onStopScan: () -> Unit = {},
    onLibrary: () -> Unit = {},
    metadataMissingCount: Int = 0,
    metadataRunning: Boolean = false,
    metadataProgress: MetadataBatchProgress? = null,
    onMetadataStart: () -> Unit = {},
    onMetadataStop: () -> Unit = {},
    metadataSessionRunning: Boolean = false,
    metadataSessionStatus: MetadataSessionStatus? = null,
    metadataSessionProgress: MetadataSessionProgress? = null,
    onMetadataSessionStart: () -> Unit = {},
    onMetadataSessionStop: () -> Unit = {},
    showStandaloneMetadataControls: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.source_registry_title),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.source_registry_description),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.add_folder), maxLines = 1, softWrap = false)
            }
            OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.refresh_sources), maxLines = 1, softWrap = false)
            }
            OutlinedButton(onClick = onLibrary, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.open_library), maxLines = 1, softWrap = false)
            }
        }
        statusMessage?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = it, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (showStandaloneMetadataControls) {
            MetadataSection(
                missingCount = metadataMissingCount,
                running = metadataRunning,
                progress = metadataProgress,
                onStart = onMetadataStart,
                onStop = onMetadataStop,
                sessionRunning = metadataSessionRunning,
                sessionStatus = metadataSessionStatus,
                sessionProgress = metadataSessionProgress,
                onSessionStart = onMetadataSessionStart,
                onSessionStop = onMetadataSessionStop,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (sources.isEmpty()) {
            Text(stringResource(R.string.no_sources))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sources, key = { it.sourceId }) { source ->
                    SourceCard(
                        source = source,
                        scanningSourceId = scanningSourceId,
                        scanPhase = scanPhase,
                        scanProgress = scanProgress,
                        scanExecutionAttached = scanExecutionAttached,
                        onRemove = onRemove,
                        onScan = onScan,
                        onPauseScan = onPauseScan,
                        onResumeScan = onResumeScan,
                        onStopScan = onStopScan,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataSection(
    missingCount: Int,
    running: Boolean,
    progress: MetadataBatchProgress?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    sessionRunning: Boolean,
    sessionStatus: MetadataSessionStatus?,
    sessionProgress: MetadataSessionProgress?,
    onSessionStart: () -> Unit,
    onSessionStop: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.metadata_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.metadata_missing_count, missingCount))
            when {
                sessionRunning -> Text(stringResource(R.string.metadata_session_running))
                sessionStatus == MetadataSessionStatus.COMPLETED ->
                    Text(stringResource(R.string.metadata_session_completed))
                sessionStatus == MetadataSessionStatus.STOPPED ->
                    Text(stringResource(R.string.metadata_session_stopped))
                sessionStatus == MetadataSessionStatus.ERROR ->
                    Text(stringResource(R.string.metadata_session_error))
            }
            progress?.let {
                Text(
                    stringResource(
                        R.string.metadata_progress,
                        it.processed,
                        it.total,
                        it.succeeded,
                        it.skipped,
                        it.failed,
                    ),
                )
            }
            sessionProgress?.let {
                Text(
                    stringResource(
                        R.string.metadata_session_progress,
                        it.pages,
                        it.processed,
                        it.succeeded,
                        it.skipped,
                        it.failed,
                    ),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (sessionRunning) {
                OutlinedButton(onClick = onSessionStop) {
                    Text(stringResource(R.string.metadata_session_stop))
                }
            } else if (running) {
                OutlinedButton(onClick = onStop) {
                    Text(stringResource(R.string.metadata_stop))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSessionStart, enabled = missingCount > 0) {
                        Text(stringResource(R.string.metadata_session_start))
                    }
                    OutlinedButton(onClick = onStart, enabled = missingCount > 0) {
                        Text(stringResource(R.string.metadata_process_page))
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: LibrarySourceEntity,
    scanningSourceId: String?,
    scanPhase: ScanPhase,
    scanProgress: ManualScanProgress?,
    scanExecutionAttached: Boolean,
    onRemove: (String) -> Unit,
    onScan: (String) -> Unit,
    onPauseScan: () -> Unit,
    onResumeScan: () -> Unit,
    onStopScan: () -> Unit,
) {
    var confirmStop by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val isCurrentScan = scanningSourceId == source.sourceId
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = sourceDisplayName(source),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            if (source.volumeName.isNullOrBlank()) {
                Text(
                    text = "Kaynak kimliği: ${source.sourceId.takeLast(6)}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = stringResource(selectionLabel(source.selectionState)),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(availabilityLabel(source.availability)),
                style = MaterialTheme.typography.bodySmall,
            )
            if (isCurrentScan) {
                Text("Aşama: ${scanPhaseLabel(scanPhase)}", style = MaterialTheme.typography.bodySmall)
                scanProgress?.let {
                    Text(
                        "İşlenen ${it.processedCount}/${it.discoveredCount} · eklenen ${it.enrichedCount} · hata ${it.errorCount + it.metadataFailedCount}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Discovery ${it.timing.discoveryMs} ms · processing ${it.timing.processingMs} ms · checkpoint ${it.timing.lastCheckpointMs} ms",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (scanPhase) {
                        ScanPhase.PAUSED -> Button(onClick = onResumeScan) { Text("Devam Et") }
                        ScanPhase.PAUSING -> OutlinedButton(onClick = {}, enabled = false) { Text("Duraklatılıyor…") }
                        ScanPhase.STOPPING -> OutlinedButton(onClick = {}, enabled = false) { Text("Durduruluyor…") }
                        ScanPhase.COMPLETED, ScanPhase.CANCELLED, ScanPhase.FAILED, ScanPhase.IDLE -> Unit
                        ScanPhase.PROCESSING -> if (scanExecutionAttached) {
                            Button(onClick = onPauseScan) { Text("Duraklat") }
                        } else {
                            Button(onClick = onResumeScan) { Text("Devam Et") }
                        }
                        else -> Button(onClick = onPauseScan) { Text("Duraklat") }
                    }
                    OutlinedButton(
                        onClick = { confirmStop = true },
                        enabled = scanPhase !in setOf(ScanPhase.PAUSING, ScanPhase.STOPPING),
                    ) {
                        Text("Durdur")
                    }
                }
                if (confirmStop) {
                    AlertDialog(
                        onDismissRequest = { confirmStop = false },
                        title = { Text("Taramayı durdur?") },
                        text = { Text("İşlenmiş katalog kayıtları korunur; devam etmek için daha sonra aynı oturumu sürdürebilirsin.") },
                        confirmButton = {
                            TextButton(onClick = { confirmStop = false; onStopScan() }) { Text("Durdur") }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmStop = false }) { Text("Vazgeç") }
                        },
                    )
                }
            } else if (source.selectionState == SourceSelectionState.ACTIVE) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onScan(source.sourceId) }, enabled = scanningSourceId == null) {
                    Text(stringResource(R.string.scan_source))
                }
            } else if (source.persistedPermissionFlags != 0 ||
                source.permissionState == com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState.GRANTED
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { onRemove(source.sourceId) }) {
                    Text(stringResource(R.string.retry_permission_release))
                }
            }
        }
    }
}

private fun scanPhaseLabel(phase: ScanPhase): String = when (phase) {
    ScanPhase.IDLE -> "Hazır"
    ScanPhase.STARTING -> "Başlatılıyor"
    ScanPhase.DISCOVERING -> "Keşfediliyor"
    ScanPhase.PROCESSING -> "İşleniyor"
    ScanPhase.PAUSING -> "Duraklatılıyor"
    ScanPhase.PAUSED -> "Duraklatıldı"
    ScanPhase.RESUMING -> "Devam ediyor"
    ScanPhase.STOPPING -> "Durduruluyor"
    ScanPhase.COMPLETED -> "Tamamlandı"
    ScanPhase.CANCELLED -> "Durduruldu"
    ScanPhase.FAILED -> "Hata"
}

@StringRes
private fun selectionLabel(state: SourceSelectionState): Int = when (state) {
    SourceSelectionState.ACTIVE -> R.string.source_active
    SourceSelectionState.REMOVED -> R.string.source_removed
}

@StringRes
private fun availabilityLabel(availability: SourceAvailability): Int = when (availability) {
    SourceAvailability.AVAILABLE -> R.string.source_available
    SourceAvailability.UNAVAILABLE -> R.string.source_unavailable
    SourceAvailability.PERMISSION_LOST -> R.string.source_permission_lost
}

@StringRes
fun sourceRegistrationMessage(result: SourceRegistrationResult): Int = when (result) {
    is SourceRegistrationResult.Added -> R.string.source_added
    is SourceRegistrationResult.AlreadyRegistered -> R.string.source_already_registered
    is SourceRegistrationResult.Reactivated -> R.string.source_reactivated
    is SourceRegistrationResult.Unavailable -> R.string.source_added_unavailable
    SourceRegistrationResult.InvalidUri -> R.string.source_invalid_uri
    SourceRegistrationResult.PermissionDenied -> R.string.source_permission_denied
    SourceRegistrationResult.PermissionFailure -> R.string.source_permission_failed
    SourceRegistrationResult.PermissionPersistedButStorageFailed ->
        R.string.source_permission_persisted_storage_failed
    SourceRegistrationResult.IdentityConflict -> R.string.source_identity_conflict
}

@StringRes
fun sourceRefreshMessage(result: SourceRefreshResult): Int = when (result) {
    is SourceRefreshResult.Available -> R.string.source_available
    is SourceRefreshResult.Unavailable -> R.string.source_unavailable
    is SourceRefreshResult.PermissionLost -> R.string.source_permission_lost
    is SourceRefreshResult.Removed -> R.string.source_removed
    SourceRefreshResult.NotFound -> R.string.source_not_found
    SourceRefreshResult.InvalidUri -> R.string.source_invalid_uri
    SourceRefreshResult.PermissionCheckFailed -> R.string.source_permission_failed
    SourceRefreshResult.StorageFailure -> R.string.source_storage_failed
}

@StringRes
fun sourceRemovalMessage(result: SourceRemovalResult): Int = when (result) {
    is SourceRemovalResult.Removed -> R.string.source_removed_success
    is SourceRemovalResult.AlreadyRemoved -> R.string.source_already_removed
    is SourceRemovalResult.RemovedWithPermissionReleaseFailure ->
        R.string.source_removed_permission_release_failed
    is SourceRemovalResult.PermissionReleasedButStorageFailed ->
        R.string.source_permission_released_storage_failed
    SourceRemovalResult.NotFound -> R.string.source_not_found
    SourceRemovalResult.StorageFailure -> R.string.source_storage_failed
}
