package com.theemrock.smoothmedia.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theemrock.smoothmedia.R
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.database.entity.MetadataRecoverySessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.MetadataRecoverySessionStatus
import com.theemrock.smoothmedia.catalog.scan.ManualScanProgress
import com.theemrock.smoothmedia.catalog.scan.ScanPhase
import com.theemrock.smoothmedia.metadata.MetadataBatchProgress
import com.theemrock.smoothmedia.metadata.MetadataSessionProgress
import com.theemrock.smoothmedia.metadata.MetadataSessionStatus
import com.theemrock.smoothmedia.metadata.MetadataRefreshPreviewResult
import com.theemrock.smoothmedia.metadata.RoomMetadataApplyDryRunSummary
import com.theemrock.smoothmedia.source.SourceRefreshResult
import com.theemrock.smoothmedia.source.SourceRegistrationResult
import com.theemrock.smoothmedia.source.SourceRemovalResult

internal fun sourceDisplayName(source: LibrarySourceEntity): String =
    source.volumeName?.trim()?.takeIf { it.isNotEmpty() }
        ?: "Müzik kaynağı"

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
    metadataPreviewRunning: Boolean = false,
    metadataPreview: MetadataRefreshPreviewResult? = null,
    onMetadataPreview: () -> Unit = {},
    metadataRecoveryStatus: MetadataRecoverySessionStatus? = null,
    metadataRecoveryProgress: MetadataRecoverySessionEntity? = null,
    onMetadataRecoveryStart: () -> Unit = {},
    onMetadataRecoveryPause: () -> Unit = {},
    onMetadataRecoveryResume: () -> Unit = {},
    onMetadataRecoveryStop: () -> Unit = {},
    metadataApplyDryRun: RoomMetadataApplyDryRunSummary? = null,
    metadataApplyDryRunRunning: Boolean = false,
    onMetadataApplyDryRun: () -> Unit = {},
    metadataApplyStatus: String? = null,
    metadataApplyRunning: Boolean = false,
    onMetadataApply: () -> Unit = {},
    autonomousApplyEnabled: Boolean = false,
    autonomousApplyToggleEnabled: Boolean = false,
    autonomousApplyStatus: String? = null,
    onAutonomousApplyToggle: (Boolean) -> Unit = {},
    showStandaloneMetadataControls: Boolean = true,
    catalogTrackCounts: Map<String, Int> = emptyMap(),
    onMenu: () -> Unit = {},
    onHome: () -> Unit = {},
) {
    var removedExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val activeSources = sources.filter { it.selectionState == SourceSelectionState.ACTIVE }
    val removedSources = sources.filter { it.selectionState == SourceSelectionState.REMOVED }
    Surface(color = SmoothMediaColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryNavigationBar(onMenu = onMenu, onDashboard = onHome, onSettings = onMenu)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 24.dp),
            ) {
                item {
                    Text("Müzik Kaynakları", color = SmoothMediaColors.TextPrimary, style = MaterialTheme.typography.headlineSmall)
                    Text("Seçilen klasörler ve gerçek tarama durumu", color = SmoothMediaColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onAdd, modifier = Modifier.weight(1f)) { Text("Klasör ekle", maxLines = 1, softWrap = false) }
                        OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) { Text("Yenile", maxLines = 1, softWrap = false) }
                    }
                    statusMessage?.let {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(it, color = SmoothMediaColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (showStandaloneMetadataControls) {
                    item {
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
                            previewRunning = metadataPreviewRunning,
                            preview = metadataPreview,
                            onPreview = onMetadataPreview,
                            recoveryStatus = metadataRecoveryStatus,
                            recoveryProgress = metadataRecoveryProgress,
                            onRecoveryStart = onMetadataRecoveryStart,
                            onRecoveryPause = onMetadataRecoveryPause,
                            onRecoveryResume = onMetadataRecoveryResume,
                            onRecoveryStop = onMetadataRecoveryStop,
                            applyDryRun = metadataApplyDryRun,
                            applyDryRunRunning = metadataApplyDryRunRunning,
                            onApplyDryRun = onMetadataApplyDryRun,
                            applyStatus = metadataApplyStatus,
                            applyRunning = metadataApplyRunning,
                            onApply = onMetadataApply,
                            autonomousApplyEnabled = autonomousApplyEnabled,
                            autonomousApplyToggleEnabled = autonomousApplyToggleEnabled,
                            autonomousApplyStatus = autonomousApplyStatus,
                            onAutonomousApplyToggle = onAutonomousApplyToggle,
                        )
                    }
                }
                item {
                    Text("Etkin kaynaklar", color = SmoothMediaColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                }
                if (activeSources.isEmpty()) {
                    item { Text("Etkin kaynak bulunmuyor.", color = SmoothMediaColors.TextMuted) }
                } else {
                    items(activeSources, key = { "active-${it.sourceId}" }) { source ->
                        SourceCard(source, catalogTrackCounts[source.sourceId], scanningSourceId, scanPhase, scanProgress, scanExecutionAttached, onRemove, onScan, onPauseScan, onResumeScan, onStopScan)
                    }
                }
                if (removedSources.isNotEmpty()) {
                    item {
                        OutlinedButton(onClick = { removedExpanded = !removedExpanded }, modifier = Modifier.fillMaxWidth()) {
                            Text("Kaldırılmış kaynaklar (${removedSources.size})")
                        }
                    }
                    if (removedExpanded) {
                        items(removedSources, key = { "removed-${it.sourceId}" }) { source ->
                            SourceCard(source, catalogTrackCounts[source.sourceId], scanningSourceId, scanPhase, scanProgress, scanExecutionAttached, onRemove, onScan, onPauseScan, onResumeScan, onStopScan)
                        }
                    }
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
    previewRunning: Boolean,
    preview: MetadataRefreshPreviewResult?,
    onPreview: () -> Unit,
    recoveryStatus: MetadataRecoverySessionStatus?,
    recoveryProgress: MetadataRecoverySessionEntity?,
    onRecoveryStart: () -> Unit,
    onRecoveryPause: () -> Unit,
    onRecoveryResume: () -> Unit,
    onRecoveryStop: () -> Unit,
    applyDryRun: RoomMetadataApplyDryRunSummary?,
    applyDryRunRunning: Boolean,
    onApplyDryRun: () -> Unit,
    applyStatus: String?,
    applyRunning: Boolean,
    onApply: () -> Unit,
    autonomousApplyEnabled: Boolean,
    autonomousApplyToggleEnabled: Boolean,
    autonomousApplyStatus: String?,
    onAutonomousApplyToggle: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.metadata_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.metadata_missing_count, missingCount))
            Text("Salt-okunur metadata audit", style = MaterialTheme.typography.titleSmall)
            Text("Otonom onarım: devre dışı (yalnız dry-run)", style = MaterialTheme.typography.bodySmall)
            Text("Apply durumu: DEVRE DIŞI", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Otonom metadata onarımı", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (autonomousApplyToggleEnabled) {
                            if (autonomousApplyEnabled) "ETKİN" else "DEVRE DIŞI"
                        } else {
                            "DEVRE DIŞI · doğrulanmış yedek gerekli"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = autonomousApplyEnabled,
                    onCheckedChange = onAutonomousApplyToggle,
                    enabled = autonomousApplyToggleEnabled,
                )
            }
            Text(
                "Yalnız kanıtlanmış yüksek güvenli Room metadata düzeltmeleri; ses dosyaları değişmez.",
                style = MaterialTheme.typography.bodySmall,
            )
            autonomousApplyStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = onApplyDryRun, enabled = !applyDryRunRunning) {
                Text(if (applyDryRunRunning) "Dry-run değerlendiriliyor..." else "Apply dry-run başlat")
            }
            applyDryRun?.let { result ->
                Text("Dry-run: ${result.evaluated} karar; uygun ${result.eligible}, değişiklik yok ${result.noChange}")
                Text(
                    "Stale ${result.skippedStale} · override ${result.skippedOverride} · okuma ${result.readError} · engelli ${result.blocked}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (result.eligible > 0 && !applyRunning && applyStatus == null) {
                    OutlinedButton(onClick = onApply) { Text("Onaylı metadata apply") }
                }
            }
            applyStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            recoveryProgress?.let {
                Text("${it.status.name}: ${it.processedTracks}/${it.totalTracks} track")
                Text(
                    "AUTO ${it.autoRepairCount} · NO_CHANGE ${it.noChangeCount} · REVIEW ${it.reviewRequiredCount} · READ_ERROR ${it.readErrorCount}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when (recoveryStatus) {
                MetadataRecoverySessionStatus.RUNNING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRecoveryPause) { Text("Duraklat") }
                    OutlinedButton(onClick = onRecoveryStop) { Text("Durdur") }
                }
                MetadataRecoverySessionStatus.PAUSED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRecoveryResume) { Text("Devam et") }
                    OutlinedButton(onClick = onRecoveryStop) { Text("Durdur") }
                }
                MetadataRecoverySessionStatus.COMPLETED,
                MetadataRecoverySessionStatus.CANCELLED,
                MetadataRecoverySessionStatus.FAILED,
                null -> Button(onClick = onRecoveryStart) { Text("Audit başlat") }
            }
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
            OutlinedButton(onClick = onPreview, enabled = !previewRunning) {
                Text(if (previewRunning) "Önizleme okunuyor..." else "Metadata önizlemesi (20)")
            }
            preview?.let { result ->
                Text("Önizleme: ${result.items.size} kayıt; Room'a yazılmadı.")
                result.items.forEach { item ->
                    val title = item.displayTitle ?: item.trackId.take(8)
                    item.rawSummary?.let { raw ->
                        Text("ham: $raw", style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    item.normalizedSummary?.let { normalized ->
                        Text("normalize: $normalized", style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    val detail = when {
                        item.status != com.theemrock.smoothmedia.metadata.MetadataPreviewStatus.READ -> item.status.name
                        item.changes.isEmpty() -> "değişiklik yok"
                        else -> item.changes.joinToString { "${it.field.name}: ${it.current ?: "∅"} → ${it.proposed ?: "∅"}" }
                    }
                    Text("$title — $detail", style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
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
    catalogTrackCount: Int?,
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
            Text("Katalog: ${catalogTrackCount?.let { "$it şarkı" } ?: "sayı bekleniyor"}", style = MaterialTheme.typography.bodySmall)
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
                    if (it.discoveredCount > 0 && it.processedCount > it.discoveredCount) {
                        Text("İlerleme sayaçları tutarsız; durum doğrulanmalı.", color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall)
                    }
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
                        text = { Text("İşlenmiş katalog kayıtları korunur; bu oturum kalıcı olarak durdurulur.") },
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
                Button(
                    onClick = { onScan(source.sourceId) },
                    enabled = scanningSourceId == null && source.availability == SourceAvailability.AVAILABLE && source.permissionState == SourcePermissionState.GRANTED,
                ) {
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
