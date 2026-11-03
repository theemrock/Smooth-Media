package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-track folder scope materialized from locator bindings.
 *
 * Maintained by SQLite triggers (see TrackFolderScopeSql) on track_locations and
 * locator_bindings inserts, so every writer keeps it in sync. Values are exactly
 * what the former recursive `track_scope`/`scopes` CTE computed per query.
 */
@Entity(tableName = "track_folder_scopes")
data class TrackFolderScopeEntity(
    @PrimaryKey
    @ColumnInfo(name = "track_id")
    val trackId: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "folder_prefix")
    val folderPrefix: String,
)
