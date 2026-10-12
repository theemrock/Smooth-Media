package com.theemrock.smoothmedia.catalog.repository

import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState

interface LibrarySourceRepository {
    fun insert(source: LibrarySourceEntity)

    fun find(sourceId: String): LibrarySourceEntity?

    fun updateState(
        sourceId: String,
        persistedPermissionFlags: Int,
        permissionState: SourcePermissionState,
        availability: SourceAvailability,
        updatedAtEpochMs: Long,
    ): Boolean
}
