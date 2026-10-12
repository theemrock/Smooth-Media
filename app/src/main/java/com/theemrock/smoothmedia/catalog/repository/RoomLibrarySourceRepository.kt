package com.theemrock.smoothmedia.catalog.repository

import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState

class RoomLibrarySourceRepository(
    private val database: CatalogDatabase,
) : LibrarySourceRepository {
    private val dao = database.librarySourceDao()

    override fun insert(source: LibrarySourceEntity) = dao.insert(source)

    override fun find(sourceId: String): LibrarySourceEntity? = dao.find(sourceId)

    override fun findByRootUri(rootUri: String): LibrarySourceEntity? = dao.findByRootUri(rootUri)

    override fun listAll(): List<LibrarySourceEntity> = dao.listAll()

    override fun registerOrReactivate(source: LibrarySourceEntity): SourceRegistrationWriteResult =
        database.runInTransaction<SourceRegistrationWriteResult> {
            val existingById = dao.find(source.sourceId)
            val existingByRoot = dao.findByRootUri(source.rootUri)
            if (existingById == null && existingByRoot == null) {
                dao.insert(source)
                return@runInTransaction SourceRegistrationWriteResult.Added
            }
            if (existingById == null || (existingByRoot != null && existingByRoot.sourceId != source.sourceId)) {
                return@runInTransaction SourceRegistrationWriteResult.Conflict
            }

            val result = if (existingById.selectionState == SourceSelectionState.REMOVED) {
                SourceRegistrationWriteResult.Reactivated
            } else {
                SourceRegistrationWriteResult.AlreadyActive
            }
            check(
                dao.updateRegistration(
                    sourceId = existingById.sourceId,
                    sourceType = source.sourceType,
                    rootUri = source.rootUri,
                    volumeName = source.volumeName,
                    persistedPermissionFlags = source.persistedPermissionFlags,
                    permissionState = source.permissionState,
                    availability = source.availability,
                    selectionState = SourceSelectionState.ACTIVE,
                    updatedAtEpochMs = source.updatedAtEpochMs,
                ) == 1,
            )
            result
        }

    override fun updateAccessState(
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

    override fun updateState(
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

    override fun markRemoved(sourceId: String, updatedAtEpochMs: Long): Boolean =
        dao.markRemoved(sourceId, updatedAtEpochMs) == 1
}
