package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "library_sources",
    indices = [Index(value = ["root_uri"], unique = true)],
)
data class LibrarySourceEntity(
    @PrimaryKey
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "source_type")
    val sourceType: LibrarySourceType,
    @ColumnInfo(name = "root_uri")
    val rootUri: String,
    @ColumnInfo(name = "volume_name")
    val volumeName: String?,
    @ColumnInfo(name = "persisted_permission_flags")
    val persistedPermissionFlags: Int,
    @ColumnInfo(name = "permission_state")
    val permissionState: SourcePermissionState,
    val availability: SourceAvailability,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
    @ColumnInfo(name = "selection_state", defaultValue = "'ACTIVE'")
    val selectionState: SourceSelectionState = SourceSelectionState.ACTIVE,
)

enum class LibrarySourceType {
    MEDIA_STORE,
    SAF_TREE,
    REMOVABLE_VOLUME,
}

enum class SourcePermissionState {
    NOT_REQUIRED,
    GRANTED,
    LOST,
}

enum class SourceAvailability {
    AVAILABLE,
    UNAVAILABLE,
    PERMISSION_LOST,
}

enum class SourceSelectionState {
    ACTIVE,
    REMOVED,
}
