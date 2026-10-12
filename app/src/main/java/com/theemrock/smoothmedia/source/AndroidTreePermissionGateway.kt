package com.theemrock.smoothmedia.source

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

object AndroidSelectedTreeParser {
    fun fromResult(uri: Uri?, grantedFlags: Int): SelectedTree? {
        if (uri == null || uri.scheme != ContentResolver.SCHEME_CONTENT || !DocumentsContract.isTreeUri(uri)) {
            return null
        }
        val authority = uri.authority ?: return null
        val treeDocumentId = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return SelectedTree(
            rootUri = uri.toString(),
            authority = authority,
            treeDocumentId = treeDocumentId,
            grantedFlags = grantedFlags,
        )
    }
}

class AndroidTreePermissionGateway(
    private val contentResolver: ContentResolver,
) : TreePermissionGateway {
    override fun takeAndVerify(selection: SelectedTree): TreePermissionTakeResult {
        val uri = parseAndValidate(selection) ?: return TreePermissionTakeResult.InvalidUri
        val readFlags = selection.grantedFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (readFlags == 0) return TreePermissionTakeResult.PermissionDenied

        try {
            contentResolver.takePersistableUriPermission(uri, readFlags)
        } catch (_: SecurityException) {
            return TreePermissionTakeResult.PermissionDenied
        } catch (_: IllegalArgumentException) {
            return TreePermissionTakeResult.InvalidUri
        } catch (_: Exception) {
            return TreePermissionTakeResult.Failed
        }
        val persistedFlags = persistedReadFlags(uri)
        if (persistedFlags == 0) return TreePermissionTakeResult.PermissionDenied

        return when (probe(uri)) {
            ProbeResult.AVAILABLE -> TreePermissionTakeResult.Granted(persistedFlags)
            ProbeResult.UNAVAILABLE -> TreePermissionTakeResult.Unavailable(persistedFlags)
            ProbeResult.PERMISSION_LOST -> TreePermissionTakeResult.PermissionDenied
            ProbeResult.INVALID -> TreePermissionTakeResult.InvalidUri
        }
    }

    override fun inspect(rootUri: String): TreePermissionInspection {
        val uri = Uri.parse(rootUri)
        if (AndroidSelectedTreeParser.fromResult(uri, 0) == null) {
            return TreePermissionInspection.InvalidUri
        }
        val persistedFlags = persistedReadFlags(uri)
        if (persistedFlags == 0) return TreePermissionInspection.PermissionLost

        return when (probe(uri)) {
            ProbeResult.AVAILABLE -> TreePermissionInspection.Available(persistedFlags)
            ProbeResult.UNAVAILABLE -> TreePermissionInspection.Unavailable(persistedFlags)
            ProbeResult.PERMISSION_LOST -> TreePermissionInspection.PermissionLost
            ProbeResult.INVALID -> TreePermissionInspection.InvalidUri
        }
    }

    override fun release(rootUri: String, persistedPermissionFlags: Int): TreePermissionReleaseResult {
        val uri = Uri.parse(rootUri)
        if (AndroidSelectedTreeParser.fromResult(uri, 0) == null) {
            return TreePermissionReleaseResult.Failed
        }
        if (persistedReadFlags(uri) == 0) return TreePermissionReleaseResult.AlreadyReleased
        val releaseFlags = (persistedPermissionFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .takeIf { it != 0 }
            ?: Intent.FLAG_GRANT_READ_URI_PERMISSION

        return try {
            contentResolver.releasePersistableUriPermission(
                uri,
                releaseFlags,
            )
            TreePermissionReleaseResult.Released
        } catch (_: SecurityException) {
            TreePermissionReleaseResult.AlreadyReleased
        } catch (_: Exception) {
            TreePermissionReleaseResult.Failed
        }
    }

    private fun parseAndValidate(selection: SelectedTree): Uri? {
        val uri = Uri.parse(selection.rootUri)
        val parsed = AndroidSelectedTreeParser.fromResult(uri, selection.grantedFlags) ?: return null
        if (parsed.authority != selection.authority || parsed.treeDocumentId != selection.treeDocumentId) return null
        return uri
    }

    private fun persistedReadFlags(uri: Uri): Int {
        val permission = contentResolver.persistedUriPermissions.firstOrNull {
            it.uri == uri && it.isReadPermission
        } ?: return 0
        return Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    private fun probe(treeUri: Uri): ProbeResult {
        val documentUri = try {
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
        } catch (_: IllegalArgumentException) {
            return ProbeResult.INVALID
        }
        return try {
            contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) ProbeResult.AVAILABLE else ProbeResult.UNAVAILABLE
            } ?: ProbeResult.UNAVAILABLE
        } catch (_: SecurityException) {
            ProbeResult.PERMISSION_LOST
        } catch (_: IllegalArgumentException) {
            ProbeResult.INVALID
        } catch (_: Exception) {
            ProbeResult.UNAVAILABLE
        }
    }

    private enum class ProbeResult {
        AVAILABLE,
        UNAVAILABLE,
        PERMISSION_LOST,
        INVALID,
    }
}
