package com.theemrock.smoothmedia.catalog.repository

import com.theemrock.smoothmedia.catalog.database.dao.LibrarySourceDao
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState

class RoomLibrarySourceRepository(
    private val dao: LibrarySourceDao,
) : LibrarySourceRepository {
    override fun insert(source: LibrarySourceEntity) = dao.insert(source)

    override fun find(sourceId: String): LibrarySourceEntity? = dao.find(sourceId)

    override fun updateState(
        sourceId: String,
        persistedPermissionFlags: Int,
        permissionState: SourcePermissionState,
        availability: SourceAvailability,
        updatedAtEpochMs: Long,
    ): Boolean = dao.updateState(
        sourceId,
        persistedPermissionFlags,
        permissionState,
        availability,
        updatedAtEpochMs,
    ) == 1
}
