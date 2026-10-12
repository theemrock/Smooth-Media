package com.theemrock.smoothmedia.catalog.repository

import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState

interface LibrarySourceRepository {
    fun insert(source: LibrarySourceEntity)

    fun find(sourceId: String): LibrarySourceEntity?

    fun findByRootUri(rootUri: String): LibrarySourceEntity?

    fun listAll(): List<LibrarySourceEntity>

    fun registerOrReactivate(source: LibrarySourceEntity): SourceRegistrationWriteResult

    fun updateAccessState(
        sourceId: String,
        persistedPermissionFlags: Int,
        permissionState: SourcePermissionState,
        availability: SourceAvailability,
        updatedAtEpochMs: Long,
    ): Boolean

    fun updateState(
        sourceId: String,
        persistedPermissionFlags: Int,
        permissionState: SourcePermissionState,
        availability: SourceAvailability,
        updatedAtEpochMs: Long,
    ): Boolean = updateAccessState(
        sourceId,
        persistedPermissionFlags,
        permissionState,
        availability,
        updatedAtEpochMs,
    )

    fun markRemoved(sourceId: String, updatedAtEpochMs: Long): Boolean
}

enum class SourceRegistrationWriteResult {
    Added,
    AlreadyActive,
    Reactivated,
    Conflict,
}
