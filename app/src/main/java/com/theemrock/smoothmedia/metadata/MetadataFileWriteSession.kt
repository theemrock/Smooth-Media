package com.theemrock.smoothmedia.metadata

import android.content.ContentResolver
import android.net.Uri
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash

data class MetadataFileWriteTarget(
    val trackId: String,
    val locationId: String,
    val sourceId: String,
    val locator: String,
    val format: AudioFormat,
    val locationAvailability: LocationAvailability,
    val sourceAvailability: SourceAvailability,
    val sourcePermission: SourcePermissionState,
    val sourceSelection: SourceSelectionState,
    val excluded: Boolean,
    val currentHash: StoredFileHash,
)

sealed interface MetadataFileWriteDecision {
    data object Allowed : MetadataFileWriteDecision
    data class Rejected(val reason: MetadataFileWriteRejection) : MetadataFileWriteDecision
}

enum class MetadataFileWriteRejection {
    UNSUPPORTED_FORMAT,
    LOCATION_UNAVAILABLE,
    SOURCE_UNAVAILABLE,
    PERMISSION_LOST,
    SOURCE_NOT_SELECTED,
    EXCLUDED,
    STALE_HASH,
    INVALID_LOCATOR,
}

object MetadataFileWritePolicy {
    fun validate(
        target: MetadataFileWriteTarget,
        observedHash: StoredFileHash?,
    ): MetadataFileWriteDecision {
        if (target.format != AudioFormat.MP3 && target.format != AudioFormat.FLAC) {
            return MetadataFileWriteDecision.Rejected(MetadataFileWriteRejection.UNSUPPORTED_FORMAT)
        }
        if (target.locator.isBlank()) {
            return MetadataFileWriteDecision.Rejected(MetadataFileWriteRejection.INVALID_LOCATOR)
        }
        if (target.excluded) return MetadataFileWriteDecision.Rejected(MetadataFileWriteRejection.EXCLUDED)
        if (target.locationAvailability != LocationAvailability.AVAILABLE) {
            return MetadataFileWriteDecision.Rejected(MetadataFileWriteRejection.LOCATION_UNAVAILABLE)
        }
        if (target.sourceAvailability != SourceAvailability.AVAILABLE) {
            return MetadataFileWriteDecision.Rejected(MetadataFileWriteRejection.SOURCE_UNAVAILABLE)
        }
        if (target.sourcePermission == SourcePermissionState.LOST) {
            return MetadataFileWriteDecision.Rejected(MetadataFileWriteRejection.PERMISSION_LOST)
        }
        if (target.sourceSelection != SourceSelectionState.ACTIVE) {
            return MetadataFileWriteDecision.Rejected(MetadataFileWriteRejection.SOURCE_NOT_SELECTED)
        }
        if (observedHash == null || observedHash != target.currentHash) {
            return MetadataFileWriteDecision.Rejected(MetadataFileWriteRejection.STALE_HASH)
        }
        return MetadataFileWriteDecision.Allowed
    }
}

sealed interface MetadataFileWriteOutcome {
    data class Written(
        val snapshot: FileTagSnapshot,
        val currentHash: StoredFileHash,
    ) : MetadataFileWriteOutcome

    data class Rejected(val reason: MetadataFileWriteRejection) : MetadataFileWriteOutcome
    data class Failed(val reason: FileTagWriteFailure) : MetadataFileWriteOutcome
}

/** Executes one explicit file write; it does not update Room. */
class MetadataFileWriteSession(
    private val contentResolver: ContentResolver,
    private val adapter: FileTagAdapter,
    private val hasher: (String) -> StoredFileHash?,
) {
    fun write(
        target: MetadataFileWriteTarget,
        change: FileTagChange,
    ): MetadataFileWriteOutcome {
        val observedHash = hasher(target.locator)
        when (val decision = MetadataFileWritePolicy.validate(target, observedHash)) {
            MetadataFileWriteDecision.Allowed -> Unit
            is MetadataFileWriteDecision.Rejected -> return MetadataFileWriteOutcome.Rejected(decision.reason)
        }
        val uri = runCatching { Uri.parse(target.locator) }.getOrNull()
            ?: return MetadataFileWriteOutcome.Rejected(MetadataFileWriteRejection.INVALID_LOCATOR)
        val descriptor = runCatching { contentResolver.openFileDescriptor(uri, "rw") }.getOrNull()
            ?: return MetadataFileWriteOutcome.Failed(FileTagWriteFailure.WRITE_FAILED)
        descriptor.use { pfd ->
            when (val result = adapter.write(pfd, change)) {
                is FileTagWriteResult.Failed -> return MetadataFileWriteOutcome.Failed(result.reason)
                is FileTagWriteResult.Written -> {
                    val newHash = hasher(target.locator)
                        ?: return MetadataFileWriteOutcome.Failed(FileTagWriteFailure.READ_BACK_FAILED)
                    return MetadataFileWriteOutcome.Written(result.snapshot, newHash)
                }
            }
        }
    }
}
