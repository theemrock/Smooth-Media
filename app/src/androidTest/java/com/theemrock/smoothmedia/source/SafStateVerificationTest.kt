package com.theemrock.smoothmedia.source

import android.database.sqlite.SQLiteDatabase
import android.provider.DocumentsContract
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.theemrock.smoothmedia.catalog.database.CatalogDatabaseFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only verification of the persisted SAF source used by P01-05. */
@RunWith(AndroidJUnit4::class)
class SafStateVerificationTest {
    @Test
    fun activeSourceHasPersistedReadPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val persistedReadUris = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()

        val databaseFile = context.getDatabasePath(CatalogDatabaseFactory.DATABASE_NAME)
        assertTrue("Catalog database is missing", databaseFile.isFile)

        val matchingSources = SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            database.query(
                "library_sources",
                arrayOf(
                    "source_id",
                    "source_type",
                    "root_uri",
                    "permission_state",
                    "availability",
                    "selection_state",
                ),
                "source_type = ? AND permission_state = ? AND availability = ? AND selection_state = ?",
                arrayOf("SAF_TREE", "GRANTED", "AVAILABLE", "ACTIVE"),
                null,
                null,
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val sourceId = cursor.getString(cursor.getColumnIndexOrThrow("source_id"))
                        val rootUri = cursor.getString(cursor.getColumnIndexOrThrow("root_uri"))
                        if (rootUri in persistedReadUris && isExpectedTestRoot(context, rootUri)) {
                            add(sourceId)
                        }
                    }
                }
            }
        }

        assertTrue(
            "No ACTIVE/GRANTED/AVAILABLE P01-05 SAF source has a persisted read grant",
            matchingSources.isNotEmpty(),
        )
        Log.i("SafStateVerificationTest", "MATCHED_SOURCE_IDS|${matchingSources.joinToString()}")
    }

    private fun isExpectedTestRoot(
        context: android.content.Context,
        rootUri: String,
    ): Boolean = runCatching {
        val treeUri = android.net.Uri.parse(rootUri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        context.contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            cursor.moveToFirst() &&
                cursor.getString(0) == "SmoothMediaSafDiscoveryTestRoot"
        } == true
    }.getOrDefault(false)
}
