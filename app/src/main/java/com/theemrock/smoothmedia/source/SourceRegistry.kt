package com.theemrock.smoothmedia.source

import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.repository.LibrarySourceRepository
import com.theemrock.smoothmedia.catalog.repository.SourceRegistrationWriteResult

class SourceRegistry(
    private val repository: LibrarySourceRepository,
    private val permissionGateway: TreePermissionGateway,
) {
    fun register(selection: SelectedTree, nowEpochMs: Long): SourceRegistrationResult {
        val identity = when (
            val identityResult = SafTreeIdentityFactory.from(selection.authority, selection.treeDocumentId)
        ) {
            SafTreeIdentityResult.Invalid -> return SourceRegistrationResult.InvalidUri
            is SafTreeIdentityResult.Valid -> identityResult.identity
        }
        val permissionResult = permissionGateway.takeAndVerify(selection)
        val persistedFlags: Int
        val availability: SourceAvailability
        when (permissionResult) {
            is TreePermissionTakeResult.Granted -> {
                persistedFlags = permissionResult.persistedPermissionFlags
                availability = SourceAvailability.AVAILABLE
            }

            is TreePermissionTakeResult.Unavailable -> {
                persistedFlags = permissionResult.persistedPermissionFlags
                availability = SourceAvailability.UNAVAILABLE
            }

            TreePermissionTakeResult.InvalidUri -> return SourceRegistrationResult.InvalidUri
            TreePermissionTakeResult.PermissionDenied -> return SourceRegistrationResult.PermissionDenied
            TreePermissionTakeResult.Failed -> return SourceRegistrationResult.PermissionFailure
        }

        val source = LibrarySourceEntity(
            sourceId = identity.sourceId,
            sourceType = LibrarySourceType.SAF_TREE,
            rootUri = selection.rootUri,
            volumeName = null,
            persistedPermissionFlags = persistedFlags,
            permissionState = SourcePermissionState.GRANTED,
            availability = availability,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
            selectionState = SourceSelectionState.ACTIVE,
        )
        val writeResult = try {
            repository.registerOrReactivate(source)
        } catch (_: Exception) {
            return SourceRegistrationResult.PermissionPersistedButStorageFailed
        }
        if (writeResult == SourceRegistrationWriteResult.Conflict) {
            return SourceRegistrationResult.IdentityConflict
        }
        if (availability == SourceAvailability.UNAVAILABLE) {
            return SourceRegistrationResult.Unavailable(identity.sourceId, writeResult)
        }
        return when (writeResult) {
            SourceRegistrationWriteResult.Added -> SourceRegistrationResult.Added(identity.sourceId)
            SourceRegistrationWriteResult.AlreadyActive -> SourceRegistrationResult.AlreadyRegistered(identity.sourceId)
            SourceRegistrationWriteResult.Reactivated -> SourceRegistrationResult.Reactivated(identity.sourceId)
            SourceRegistrationWriteResult.Conflict -> SourceRegistrationResult.IdentityConflict
        }
    }

    fun refresh(sourceId: String, nowEpochMs: Long): SourceRefreshResult {
        val source = try {
            repository.find(sourceId)
        } catch (_: Exception) {
            return SourceRefreshResult.StorageFailure
        } ?: return SourceRefreshResult.NotFound
        if (source.selectionState == SourceSelectionState.REMOVED) {
            return SourceRefreshResult.Removed(sourceId)
        }

        val inspection = permissionGateway.inspect(source.rootUri)
        val updated = try {
            when (inspection) {
                is TreePermissionInspection.Available -> repository.updateAccessState(
                    sourceId,
                    inspection.persistedPermissionFlags,
                    SourcePermissionState.GRANTED,
                    SourceAvailability.AVAILABLE,
                    nowEpochMs,
                )

                is TreePermissionInspection.Unavailable -> repository.updateAccessState(
                    sourceId,
                    inspection.persistedPermissionFlags,
                    SourcePermissionState.GRANTED,
                    SourceAvailability.UNAVAILABLE,
                    nowEpochMs,
                )

                TreePermissionInspection.PermissionLost -> repository.updateAccessState(
                    sourceId,
                    0,
                    SourcePermissionState.LOST,
                    SourceAvailability.PERMISSION_LOST,
                    nowEpochMs,
                )

                TreePermissionInspection.InvalidUri -> return SourceRefreshResult.InvalidUri
                TreePermissionInspection.Failed -> return SourceRefreshResult.PermissionCheckFailed
            }
        } catch (_: Exception) {
            return SourceRefreshResult.StorageFailure
        }
        if (!updated) return SourceRefreshResult.StorageFailure

        return when (inspection) {
            is TreePermissionInspection.Available -> SourceRefreshResult.Available(sourceId)
            is TreePermissionInspection.Unavailable -> SourceRefreshResult.Unavailable(sourceId)
            TreePermissionInspection.PermissionLost -> SourceRefreshResult.PermissionLost(sourceId)
            TreePermissionInspection.InvalidUri -> SourceRefreshResult.InvalidUri
            TreePermissionInspection.Failed -> SourceRefreshResult.PermissionCheckFailed
        }
    }

    fun remove(sourceId: String, nowEpochMs: Long): SourceRemovalResult {
        val source = try {
            repository.find(sourceId)
        } catch (_: Exception) {
            return SourceRemovalResult.StorageFailure
        } ?: return SourceRemovalResult.NotFound
        val wasRemoved = source.selectionState == SourceSelectionState.REMOVED
        if (!wasRemoved) {
            val removed = try {
                repository.markRemoved(sourceId, nowEpochMs)
            } catch (_: Exception) {
                false
            }
            if (!removed) return SourceRemovalResult.StorageFailure
        }

        return when (permissionGateway.release(source.rootUri, source.persistedPermissionFlags)) {
            TreePermissionReleaseResult.Failed ->
                SourceRemovalResult.RemovedWithPermissionReleaseFailure(sourceId)

            TreePermissionReleaseResult.AlreadyReleased,
            TreePermissionReleaseResult.Released,
            -> if (wasRemoved) {
                SourceRemovalResult.AlreadyRemoved(sourceId)
            } else {
                SourceRemovalResult.Removed(sourceId)
            }
        }
    }
}
