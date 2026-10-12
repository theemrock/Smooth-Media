package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState

@Dao
interface LibrarySourceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(source: LibrarySourceEntity)

    @Query("SELECT * FROM library_sources WHERE source_id = :sourceId")
    fun find(sourceId: String): LibrarySourceEntity?

    @Query("SELECT * FROM library_sources WHERE root_uri = :rootUri")
    fun findByRootUri(rootUri: String): LibrarySourceEntity?

    @Query("SELECT * FROM library_sources ORDER BY created_at_epoch_ms, source_id")
    fun listAll(): List<LibrarySourceEntity>

    @Query(
        """
        UPDATE library_sources
        SET source_type = :sourceType,
            root_uri = :rootUri,
            volume_name = :volumeName,
            persisted_permission_flags = :persistedPermissionFlags,
            permission_state = :permissionState,
            availability = :availability,
            selection_state = :selectionState,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE source_id = :sourceId
        """,
    )
    fun updateRegistration(
        sourceId: String,
        sourceType: com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType,
        rootUri: String,
        volumeName: String?,
        persistedPermissionFlags: Int,
        permissionState: SourcePermissionState,
        availability: SourceAvailability,
        selectionState: SourceSelectionState,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE library_sources
        SET persisted_permission_flags = :persistedPermissionFlags,
            permission_state = :permissionState,
            availability = :availability,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE source_id = :sourceId
        """,
    )
    fun updateState(
        sourceId: String,
        persistedPermissionFlags: Int,
        permissionState: SourcePermissionState,
        availability: SourceAvailability,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE library_sources
        SET selection_state = 'REMOVED',
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE source_id = :sourceId
          AND selection_state != 'REMOVED'
        """,
    )
    fun markRemoved(sourceId: String, updatedAtEpochMs: Long): Int
}
