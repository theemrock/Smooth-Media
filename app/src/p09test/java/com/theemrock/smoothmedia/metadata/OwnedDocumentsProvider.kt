package com.theemrock.smoothmedia.metadata

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

/** Test-only p09test DocumentsProvider; all data stays below the p09test files directory. */
class OwnedDocumentsProvider : DocumentsProvider() {
    override fun onCreate(): Boolean = documentsDirectory().let { it.mkdirs() || it.isDirectory }

    override fun queryRoots(projection: Array<String>?): Cursor = MatrixCursor(projection ?: ROOT_PROJECTION).also { cursor ->
        val columns = projection ?: ROOT_PROJECTION
        cursor.addRow(columns.map { rootValue(it) }.toTypedArray())
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method != METHOD_SEED) return super.call(method, arg, extras) ?: throw IllegalArgumentException("Unsupported provider call")
        if (arg == null || !isSafeId(arg)) throw IllegalArgumentException("Invalid test fixture call")
        val bytes = extras?.getByteArray(EXTRA_BYTES) ?: throw IllegalArgumentException("Fixture bytes are required")
        documentsDirectory().mkdirs()
        File(documentsDirectory(), arg).writeBytes(bytes)
        return Bundle().apply {
            putString(EXTRA_PROVIDER_PACKAGE, requireNotNull(context).packageName)
            putInt(EXTRA_PROVIDER_UID, android.os.Process.myUid())
        }
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor = singleDocument(documentId, projection)

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<String>?, sortOrder: String?): Cursor {
        if (parentDocumentId != ROOT_ID) throw FileNotFoundException("Only root is a directory")
        val columns = projection ?: DOCUMENT_PROJECTION
        return MatrixCursor(columns).also { cursor ->
            documentsDirectory().listFiles()?.sortedBy { it.name }?.forEach { addDocumentRow(cursor, columns, it) }
        }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val file = documentFile(documentId)
        if (!file.isFile) throw FileNotFoundException("Not a document: $documentId")
        val flags = when (mode) {
            "r" -> ParcelFileDescriptor.MODE_READ_ONLY
            "rw" -> ParcelFileDescriptor.MODE_READ_WRITE
            "rwt" -> ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE
            else -> throw FileNotFoundException("Unsupported mode: $mode")
        }
        return try {
            ParcelFileDescriptor.open(file, flags)
        } catch (error: IOException) {
            throw FileNotFoundException("Cannot open: $file").also { it.initCause(error) }
        }
    }

    override fun getDocumentType(documentId: String): String = mimeType(documentFile(documentId))

    override fun createDocument(parentDocumentId: String, mimeType: String?, displayName: String): String {
        if (parentDocumentId != ROOT_ID) throw FileNotFoundException("Only root accepts documents")
        val file = File(documentsDirectory(), "${UUID.randomUUID()}-${safeName(displayName)}")
        if (!file.createNewFile()) throw FileNotFoundException("Cannot create document: $displayName")
        return file.name
    }

    override fun deleteDocument(documentId: String) {
        val file = documentFile(documentId)
        if (!file.isFile || !file.delete()) throw FileNotFoundException("Cannot delete: $documentId")
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val source = documentFile(documentId)
        if (!source.isFile) throw FileNotFoundException("Cannot rename: $documentId")
        val target = File(documentsDirectory(), "${UUID.randomUUID()}-${safeName(displayName)}")
        if (!source.renameTo(target)) throw FileNotFoundException("Cannot rename: $documentId")
        return target.name
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        parentDocumentId == ROOT_ID && isSafeId(documentId) && documentFile(documentId).isFile

    private fun singleDocument(documentId: String, projection: Array<String>?): Cursor {
        val columns = projection ?: DOCUMENT_PROJECTION
        return MatrixCursor(columns).also { cursor ->
            if (documentId == ROOT_ID) cursor.addRow(columns.map { rootDocumentValue(it) }.toTypedArray())
            else addDocumentRow(cursor, columns, documentFile(documentId))
        }
    }

    private fun addDocumentRow(cursor: MatrixCursor, columns: Array<String>, file: File) {
        cursor.addRow(columns.map { column ->
            when (column) {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID -> file.name
                DocumentsContract.Document.COLUMN_DISPLAY_NAME -> file.name
                DocumentsContract.Document.COLUMN_MIME_TYPE -> mimeType(file)
                DocumentsContract.Document.COLUMN_FLAGS -> DocumentsContract.Document.FLAG_SUPPORTS_WRITE or DocumentsContract.Document.FLAG_SUPPORTS_DELETE or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                DocumentsContract.Document.COLUMN_SIZE -> file.length()
                DocumentsContract.Document.COLUMN_LAST_MODIFIED -> file.lastModified()
                else -> null
            }
        }.toTypedArray())
    }

    private fun rootValue(column: String): Any? = when (column) {
        DocumentsContract.Root.COLUMN_ROOT_ID -> ROOT_ID
        DocumentsContract.Root.COLUMN_MIME_TYPES -> "audio/*\napplication/octet-stream"
        DocumentsContract.Root.COLUMN_FLAGS -> DocumentsContract.Root.FLAG_SUPPORTS_CREATE
        DocumentsContract.Root.COLUMN_TITLE -> "Test-owned metadata documents"
        DocumentsContract.Root.COLUMN_SUMMARY -> "Private instrumentation area"
        DocumentsContract.Root.COLUMN_DOCUMENT_ID -> ROOT_ID
        else -> null
    }

    private fun rootDocumentValue(column: String): Any? = when (column) {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID -> ROOT_ID
        DocumentsContract.Document.COLUMN_DISPLAY_NAME -> "Test-owned root"
        DocumentsContract.Document.COLUMN_MIME_TYPE -> DocumentsContract.Document.MIME_TYPE_DIR
        DocumentsContract.Document.COLUMN_FLAGS -> DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        else -> null
    }

    private fun documentsDirectory(): File = File(requireNotNull(context) { "Provider is not attached" }.filesDir, "documents")

    private fun documentFile(documentId: String): File {
        if (!isSafeId(documentId)) throw FileNotFoundException("Invalid document id")
        return File(documentsDirectory(), documentId)
    }

    private fun isSafeId(documentId: String): Boolean =
        documentId.isNotEmpty() && documentId != ROOT_ID && !documentId.contains('/') && !documentId.contains('\\') && !documentId.contains("..")

    private fun safeName(displayName: String): String {
        if (displayName.isEmpty() || displayName.contains('/') || displayName.contains('\\') || displayName.contains("..")) throw FileNotFoundException("Invalid display name")
        return displayName
    }

    private fun mimeType(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(file.name)) ?: "application/octet-stream"

    companion object {
        const val AUTHORITY = "com.theemrock.smoothmedia.p09test.documents"
        const val ROOT_ID = "root"
        const val METHOD_SEED = "seed_test_document"
        const val EXTRA_BYTES = "bytes"
        const val EXTRA_PROVIDER_PACKAGE = "provider_package"
        const val EXTRA_PROVIDER_UID = "provider_uid"
        val ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
        )
        val DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
