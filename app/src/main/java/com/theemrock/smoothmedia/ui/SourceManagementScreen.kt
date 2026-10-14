package com.theemrock.smoothmedia.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theemrock.smoothmedia.R
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.source.SourceRefreshResult
import com.theemrock.smoothmedia.source.SourceRegistrationResult
import com.theemrock.smoothmedia.source.SourceRemovalResult

@Composable
fun SourceManagementScreen(
    sources: List<LibrarySourceEntity>,
    statusMessage: String?,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
    onRemove: (String) -> Unit,
    scanningSourceId: String? = null,
    onScan: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onAdd) {
                Text(stringResource(R.string.add_folder))
            }
            OutlinedButton(onClick = onRefresh) {
                Text(stringResource(R.string.refresh_sources))
            }
        }
        statusMessage?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = it, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (sources.isEmpty()) {
            Text(stringResource(R.string.no_sources))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sources, key = { it.sourceId }) { source ->
                    SourceCard(source, scanningSourceId, onRemove, onScan)
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: LibrarySourceEntity,
    scanningSourceId: String?,
    onRemove: (String) -> Unit,
    onScan: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = source.rootUri,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(selectionLabel(source.selectionState)),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(availabilityLabel(source.availability)),
                style = MaterialTheme.typography.bodySmall,
            )
            if (source.selectionState == SourceSelectionState.ACTIVE) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onScan(source.sourceId) },
                        enabled = scanningSourceId == null,
                    ) {
                        Text(
                            if (scanningSourceId == source.sourceId) {
                                stringResource(R.string.scan_running)
                            } else {
                                stringResource(R.string.scan_source)
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = { onRemove(source.sourceId) },
                        enabled = scanningSourceId == null,
                    ) {
                        Text(stringResource(R.string.remove_source))
                    }
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
