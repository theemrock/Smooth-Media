package com.theemrock.smoothmedia.discovery

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract

/** Reads names and file facts from a previously granted SAF tree without reading file contents. */
class AndroidSafDocumentTreeReader(
    private val contentResolver: ContentResolver,
    private val timing: SafDiscoveryTimingCollector = SafDiscoveryTimingCollector(),
) : SafDocumentTreeReader {
    override fun listChildren(directoryUri: String): DirectoryReadResult {
        val parent = try {
            measureUriCreation { Uri.parse(directoryUri) }
        } catch (_: Exception) {
            return DirectoryReadResult.Failed
        }
        val documentId = directoryDocumentId(parent) ?: return DirectoryReadResult.Failed
        val childrenUri = try {
            measureUriCreation { DocumentsContract.buildChildDocumentsUriUsingTree(parent, documentId) }
        } catch (_: IllegalArgumentException) {
            return DirectoryReadResult.Failed
        }

        return try {
            val cursor = measureQuery {
                contentResolver.query(childrenUri, PROJECTION, null, null, null)
            }
                ?: return DirectoryReadResult.Unavailable
            cursor.use { readDocuments(it, parent) }
        } catch (_: SecurityException) {
            DirectoryReadResult.PermissionLost
        } catch (_: Exception) {
            DirectoryReadResult.Unavailable
        }
    }

    private fun readDocuments(cursor: Cursor, parentUri: Uri): DirectoryReadResult {
        val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        if (idColumn < 0 || nameColumn < 0 || mimeColumn < 0) {
            return DirectoryReadResult.Readable(emptyList(), unreadableEntryCount = cursor.count)
        }

        val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val documents = mutableListOf<TreeDocument>()
        var unreadableEntries = 0
        while (cursor.moveToNext()) {
            val document = try {
                val documentId = cursor.getString(idColumn)
                val displayName = cursor.getString(nameColumn)
                if (documentId.isNullOrBlank() || displayName.isNullOrBlank()) {
                    null
                } else {
                    val mimeType = cursor.getString(mimeColumn)
                    val uri = measureUriCreation {
                        DocumentsContract.buildDocumentUriUsingTree(parentUri, documentId)
                    }
                    TreeDocument(
                        documentId = documentId,
                        parentDocumentId = directoryDocumentId(parentUri),
                        uri = uri.toString(),
                        displayName = displayName,
                        mimeType = mimeType,
                        isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
                        sizeBytes = cursor.longOrNull(sizeColumn),
                        modifiedAtEpochMs = cursor.longOrNull(modifiedColumn),
                    )
                }
            } catch (_: Exception) {
                null
            }
            if (document == null) unreadableEntries++ else documents += document
        }
        return DirectoryReadResult.Readable(documents, unreadableEntries)
    }

    private fun Cursor.longOrNull(columnIndex: Int): Long? = try {
        if (columnIndex < 0 || isNull(columnIndex)) null else getLong(columnIndex)
    } catch (_: Exception) {
        null
    }

    private inline fun <T> measureQuery(block: () -> T): T {
        val startedAt = System.nanoTime()
        return try {
            block()
        } finally {
            timing.recordQuery(System.nanoTime() - startedAt)
        }
    }

    private inline fun <T> measureUriCreation(block: () -> T): T {
        val startedAt = System.nanoTime()
        return try {
            block()
        } finally {
            timing.recordUriCreation(System.nanoTime() - startedAt)
        }
    }

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}

internal fun directoryDocumentId(directoryUri: Uri): String? = try {
    if (DocumentsContract.isTreeUri(directoryUri)) {
        try {
            DocumentsContract.getDocumentId(directoryUri)
        } catch (_: IllegalArgumentException) {
            DocumentsContract.getTreeDocumentId(directoryUri)
        }
    } else {
        DocumentsContract.getDocumentId(directoryUri)
    }
} catch (_: IllegalArgumentException) {
    null
}
