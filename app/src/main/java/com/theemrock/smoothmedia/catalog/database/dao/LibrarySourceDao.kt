package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState

@Dao
interface LibrarySourceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(source: LibrarySourceEntity)

    @Query("SELECT * FROM library_sources WHERE source_id = :sourceId")
    fun find(sourceId: String): LibrarySourceEntity?

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
}
