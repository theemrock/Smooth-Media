package com.theemrock.smoothmedia.source

data class SelectedTree(
    val rootUri: String,
    val authority: String?,
    val treeDocumentId: String?,
    val grantedFlags: Int,
)

interface TreePermissionGateway {
    fun takeAndVerify(selection: SelectedTree): TreePermissionTakeResult

    /** Provider display name is presentation-only and never replaces source identity. */
    fun displayName(rootUri: String): String? = null

    fun inspect(rootUri: String): TreePermissionInspection

    fun release(rootUri: String, persistedPermissionFlags: Int): TreePermissionReleaseResult
}

sealed interface TreePermissionTakeResult {
    data class Granted(val persistedPermissionFlags: Int) : TreePermissionTakeResult

    data class Unavailable(val persistedPermissionFlags: Int) : TreePermissionTakeResult

    data object PermissionDenied : TreePermissionTakeResult

    data object InvalidUri : TreePermissionTakeResult

    data object Failed : TreePermissionTakeResult
}

sealed interface TreePermissionInspection {
    data class Available(val persistedPermissionFlags: Int) : TreePermissionInspection

    data class Unavailable(val persistedPermissionFlags: Int) : TreePermissionInspection

    data object PermissionLost : TreePermissionInspection

    data object InvalidUri : TreePermissionInspection

    data object Failed : TreePermissionInspection
}

sealed interface TreePermissionReleaseResult {
    data object Released : TreePermissionReleaseResult

    data object AlreadyReleased : TreePermissionReleaseResult

    data object Failed : TreePermissionReleaseResult
}
