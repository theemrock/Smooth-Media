package com.theemrock.smoothmedia.source

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

object AndroidSelectedTreeParser {
    fun fromResult(uri: Uri?, grantedFlags: Int): SelectedTree? {
        if (uri == null || uri.scheme != ContentResolver.SCHEME_CONTENT || !DocumentsContract.isTreeUri(uri)) return null
        val authority = uri.authority ?: return null
        val treeDocumentId = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return SelectedTree(uri.toString(), authority, treeDocumentId, grantedFlags)
    }
}

data class PersistedTreePermission(
    val uri: Uri,
    val isReadPermission: Boolean,
    val isWritePermission: Boolean = false,
) {
    val persistedFlags: Int
        get() = (if (isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
}

enum class TreeProbeResult { AVAILABLE, UNAVAILABLE, PERMISSION_LOST, INVALID, FAILED }

/** Small platform boundary that keeps Android grant failures testable and controlled. */
interface AndroidTreePermissionOperations {
    fun take(uri: Uri, flags: Int)
    fun persistedPermissions(): List<PersistedTreePermission>
    fun release(uri: Uri, flags: Int)
    fun probe(treeUri: Uri): TreeProbeResult

    fun queryDisplayName(documentUri: Uri): String? = null
}

class AndroidTreePermissionGateway(
    private val operations: AndroidTreePermissionOperations,
) : TreePermissionGateway {
    constructor(contentResolver: ContentResolver) : this(ContentResolverTreePermissionOperations(contentResolver))

    override fun takeAndVerify(selection: SelectedTree): TreePermissionTakeResult {
        val uri = parseAndValidate(selection) ?: return TreePermissionTakeResult.InvalidUri
        val requestedFlags = selection.grantedFlags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (requestedFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) {
            return TreePermissionTakeResult.PermissionDenied
        }
        try {
            operations.take(uri, requestedFlags)
        } catch (_: SecurityException) {
            return TreePermissionTakeResult.PermissionDenied
        } catch (_: IllegalArgumentException) {
            return TreePermissionTakeResult.InvalidUri
        } catch (_: Exception) {
            return TreePermissionTakeResult.Failed
        }
        val persistedFlags = try {
            persistedGrantFlags(uri)
        } catch (_: Exception) {
            return TreePermissionTakeResult.Failed
        }
        if (persistedFlags == 0) return TreePermissionTakeResult.PermissionDenied
        return when (safeProbe(uri)) {
            TreeProbeResult.AVAILABLE -> TreePermissionTakeResult.Granted(persistedFlags)
            TreeProbeResult.UNAVAILABLE -> TreePermissionTakeResult.Unavailable(persistedFlags)
            TreeProbeResult.PERMISSION_LOST -> TreePermissionTakeResult.PermissionDenied
            TreeProbeResult.INVALID -> TreePermissionTakeResult.InvalidUri
            TreeProbeResult.FAILED -> TreePermissionTakeResult.Failed
        }
    }

    override fun displayName(rootUri: String): String? {
        val treeUri = Uri.parse(rootUri)
        val documentUri = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        }.getOrNull() ?: return null
        return runCatching { operations.queryDisplayName(documentUri) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    override fun inspect(rootUri: String): TreePermissionInspection {
        val uri = Uri.parse(rootUri)
        if (AndroidSelectedTreeParser.fromResult(uri, 0) == null) return TreePermissionInspection.InvalidUri
        val persistedFlags = try {
            persistedGrantFlags(uri)
        } catch (_: Exception) {
            return TreePermissionInspection.Failed
        }
        if (persistedFlags == 0) return TreePermissionInspection.PermissionLost
        return when (safeProbe(uri)) {
            TreeProbeResult.AVAILABLE -> TreePermissionInspection.Available(persistedFlags)
            TreeProbeResult.UNAVAILABLE -> TreePermissionInspection.Unavailable(persistedFlags)
            TreeProbeResult.PERMISSION_LOST -> TreePermissionInspection.PermissionLost
            TreeProbeResult.INVALID -> TreePermissionInspection.InvalidUri
            TreeProbeResult.FAILED -> TreePermissionInspection.Failed
        }
    }

    override fun release(rootUri: String, persistedPermissionFlags: Int): TreePermissionReleaseResult {
        val uri = Uri.parse(rootUri)
        if (AndroidSelectedTreeParser.fromResult(uri, 0) == null) return TreePermissionReleaseResult.Failed
        val matching = try {
            matchingPermissions(uri)
        } catch (_: Exception) {
            return TreePermissionReleaseResult.Failed
        }
        if (matching.isEmpty()) return TreePermissionReleaseResult.AlreadyReleased
        val releaseFlags = (persistedPermissionFlags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            .takeIf { it != 0 } ?: Intent.FLAG_GRANT_READ_URI_PERMISSION
        matching.forEach { permission ->
            try {
                operations.release(permission.uri, releaseFlags)
            } catch (_: Exception) {
                // The postcondition below decides whether release actually completed.
            }
        }
        val remaining = try {
            matchingPermissions(uri)
        } catch (_: Exception) {
            return TreePermissionReleaseResult.Failed
        }
        return if (remaining.isEmpty()) TreePermissionReleaseResult.Released else TreePermissionReleaseResult.Failed
    }

    private fun parseAndValidate(selection: SelectedTree): Uri? {
        val uri = Uri.parse(selection.rootUri)
        val parsed = AndroidSelectedTreeParser.fromResult(uri, selection.grantedFlags) ?: return null
        if (parsed.authority != selection.authority || parsed.treeDocumentId != selection.treeDocumentId) return null
        return uri
    }

    private fun persistedGrantFlags(uri: Uri): Int =
        matchingPermissions(uri).fold(0) { flags, permission -> flags or permission.persistedFlags }

    private fun matchingPermissions(uri: Uri): List<PersistedTreePermission> {
        val identity = treeIdentity(uri) ?: return emptyList()
        return operations.persistedPermissions().filter { permission ->
            permission.persistedFlags != 0 && treeIdentity(permission.uri) == identity
        }
    }

    private fun safeProbe(uri: Uri): TreeProbeResult = try {
        operations.probe(uri)
    } catch (_: Exception) {
        TreeProbeResult.FAILED
    }

    private fun treeIdentity(uri: Uri): Pair<String, String>? {
        val parsed = AndroidSelectedTreeParser.fromResult(uri, 0) ?: return null
        return parsed.authority.orEmpty() to parsed.treeDocumentId.orEmpty()
    }
}

private class ContentResolverTreePermissionOperations(
    private val contentResolver: ContentResolver,
) : AndroidTreePermissionOperations {
    override fun take(uri: Uri, flags: Int) = contentResolver.takePersistableUriPermission(uri, flags)

    override fun persistedPermissions(): List<PersistedTreePermission> =
        contentResolver.persistedUriPermissions.map {
            PersistedTreePermission(
                uri = it.uri,
                isReadPermission = it.isReadPermission,
                isWritePermission = it.isWritePermission,
            )
        }

    override fun release(uri: Uri, flags: Int) = contentResolver.releasePersistableUriPermission(uri, flags)

    override fun probe(treeUri: Uri): TreeProbeResult {
        val documentUri = try {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        } catch (_: IllegalArgumentException) {
            return TreeProbeResult.INVALID
        }
        return try {
            contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null,
            )?.use { if (it.moveToFirst()) TreeProbeResult.AVAILABLE else TreeProbeResult.UNAVAILABLE }
                ?: TreeProbeResult.UNAVAILABLE
        } catch (_: SecurityException) {
            TreeProbeResult.PERMISSION_LOST
        } catch (_: IllegalArgumentException) {
            TreeProbeResult.INVALID
        } catch (_: Exception) {
            TreeProbeResult.UNAVAILABLE
        }
    }

    override fun queryDisplayName(documentUri: Uri): String? = contentResolver.query(
        documentUri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}
