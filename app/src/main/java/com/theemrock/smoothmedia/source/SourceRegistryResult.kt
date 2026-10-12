package com.theemrock.smoothmedia.source

import com.theemrock.smoothmedia.catalog.repository.SourceRegistrationWriteResult

sealed interface SourceRegistrationResult {
    data class Added(val sourceId: String) : SourceRegistrationResult

    data class AlreadyRegistered(val sourceId: String) : SourceRegistrationResult

    data class Reactivated(val sourceId: String) : SourceRegistrationResult

    data class Unavailable(
        val sourceId: String,
        val writeResult: SourceRegistrationWriteResult,
    ) : SourceRegistrationResult

    data object InvalidUri : SourceRegistrationResult

    data object PermissionDenied : SourceRegistrationResult

    data object PermissionFailure : SourceRegistrationResult

    data object PermissionPersistedButStorageFailed : SourceRegistrationResult

    data object IdentityConflict : SourceRegistrationResult
}

sealed interface SourceRefreshResult {
    data class Available(val sourceId: String) : SourceRefreshResult

    data class Unavailable(val sourceId: String) : SourceRefreshResult

    data class PermissionLost(val sourceId: String) : SourceRefreshResult

    data class Removed(val sourceId: String) : SourceRefreshResult

    data object NotFound : SourceRefreshResult

    data object InvalidUri : SourceRefreshResult

    data object PermissionCheckFailed : SourceRefreshResult

    data object StorageFailure : SourceRefreshResult
}

sealed interface SourceRemovalResult {
    data class Removed(val sourceId: String) : SourceRemovalResult

    data class AlreadyRemoved(val sourceId: String) : SourceRemovalResult

    data class RemovedWithPermissionReleaseFailure(val sourceId: String) : SourceRemovalResult

    data object NotFound : SourceRemovalResult

    data object StorageFailure : SourceRemovalResult
}
