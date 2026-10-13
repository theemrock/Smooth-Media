package com.theemrock.smoothmedia.discovery

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.theemrock.smoothmedia.catalog.database.CatalogDatabaseFactory
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.discovery.DirectoryReadResult.Readable
import com.theemrock.smoothmedia.source.AndroidTreePermissionGateway
import com.theemrock.smoothmedia.source.TreePermissionGateway
import com.theemrock.smoothmedia.source.TreePermissionInspection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in only: exercises a preselected, persisted test tree through the real Android provider. */
@RunWith(AndroidJUnit4::class)
class SelectedAudioDiscoveryRealSafTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun discoversFixturesFromOnlyThePersistedSelectedTree() {
        val arguments = InstrumentationRegistry.getArguments()
        assume(
            enabled = arguments.getString(ARG_ENABLED).toBoolean(),
            reason = "Enable explicitly with -e $ARG_ENABLED true",
        )
        val sourceId = arguments.getString(ARG_SOURCE_ID)
        assume(sourceId != null, "Pass -e $ARG_SOURCE_ID=<existing ACTIVE SAF source ID>")

        val source = findSavedSourceReadOnly(context, sourceId!!)
            ?: skip("No saved LibrarySource exists for $ARG_SOURCE_ID")
            assume(
                source.sourceType == LibrarySourceType.SAF_TREE &&
                    source.selectionState == SourceSelectionState.ACTIVE,
                "The selected source must be an ACTIVE SAF_TREE",
            )
            assume(
                rootDisplayName(context, source.rootUri) == TEST_ROOT_NAME,
                "Selected tree must be the dedicated $TEST_ROOT_NAME test root",
            )

            val permissions: TreePermissionGateway = AndroidTreePermissionGateway(context.contentResolver)
            val inspection = permissions.inspect(source.rootUri)
            assume(
                inspection is TreePermissionInspection.Available,
                "The test tree needs an available persisted read grant; got $inspection",
            )

            val treeReader = AndroidSafDocumentTreeReader(context.contentResolver)
            val discovery = SelectedAudioDiscovery(permissions = permissions, treeReader = treeReader)
            val rootListing = requireReadable(
                treeReader.listChildren(source.rootUri),
                "the selected test root",
            )
            val rootChildrenSummary = rootListing.children.joinToString { child ->
                child.displayName + if (child.isDirectory) "/" else ""
            }
            Log.i(
                TAG,
                "ROOT_CHILDREN|$rootChildrenSummary",
            )
            assertTrue(
                "$ROOT_MP3_NAME must be a direct child of the selected root; " +
                    "found=$rootChildrenSummary",
                rootListing.children.any { it.displayName == ROOT_MP3_NAME && !it.isDirectory },
            )
            val nestedDirectory = rootListing.children.singleOrNull {
                it.displayName == NESTED_DIRECTORY_NAME && it.isDirectory
            }
            assertNotNull("Missing $NESTED_DIRECTORY_NAME fixture directory", nestedDirectory)
            val nestedListing = requireReadable(
                treeReader.listChildren(nestedDirectory!!.uri),
                "$NESTED_DIRECTORY_NAME",
            )
            Log.i(TAG, "NESTED_CHILDREN|${nestedListing.children.map { it.displayName }}")
            val nestedDocumentId = DocumentsContract.getDocumentId(Uri.parse(nestedDirectory.uri))
            val providerNestedChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                Uri.parse(source.rootUri),
                nestedDocumentId,
            )
            val providerNestedChildren = context.contentResolver.query(
                providerNestedChildrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                buildList<String> {
                    val nameColumn = cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    )
                    while (cursor.moveToNext()) add(cursor.getString(nameColumn))
                }
            } ?: error("Provider returned no cursor for the nested fixture directory")
            Log.i(TAG, "PROVIDER_NESTED_CHILDREN|$providerNestedChildren")
            assertTrue(
                "$NESTED_FLAC_NAME must be inside $NESTED_DIRECTORY_NAME",
                NESTED_FLAC_NAME in providerNestedChildren,
            )

            val first = discovery.discover(source)
            if (first is AudioDiscoveryResult.PermissionLost || first is AudioDiscoveryResult.SourceUnavailable) {
                skip("The selected test tree or its persisted grant became unavailable during discovery: $first")
            }
            assertTrue("Discovery should complete: $first", first is AudioDiscoveryResult.Completed)
            first as AudioDiscoveryResult.Completed

            val rootMp3 = first.files.singleOrNull { it.displayName == ROOT_MP3_NAME }
            val nestedFlac = first.files.singleOrNull { it.displayName == NESTED_FLAC_NAME }
            assertNotNull("Missing root MP3 fixture $ROOT_MP3_NAME", rootMp3)
            assertNotNull("Missing nested FLAC fixture $NESTED_FLAC_NAME", nestedFlac)
            assertEquals(AudioFormat.MP3, rootMp3!!.format)
            assertEquals(AudioFormat.FLAC, nestedFlac!!.format)
            assertTrue(Uri.parse(rootMp3.uri).scheme == "content")
            assertTrue(Uri.parse(nestedFlac.uri).scheme == "content")

            val selectedTreeId = DocumentsContract.getTreeDocumentId(Uri.parse(source.rootUri))
            assertTrue(
                "Every discovered document must remain under the selected tree",
                first.files.all { file ->
                    DocumentsContract.getTreeDocumentId(Uri.parse(file.uri)) == selectedTreeId
                },
            )
            assertEquals(
                "One provider document must appear at most once per discovery",
                first.files.size,
                first.files.map { it.documentId }.toSet().size,
            )

            val second = discovery.discover(source)
            if (second is AudioDiscoveryResult.PermissionLost || second is AudioDiscoveryResult.SourceUnavailable) {
                skip("The selected test tree or its persisted grant became unavailable on repeat discovery: $second")
            }
            assertTrue("Repeat discovery should complete: $second", second is AudioDiscoveryResult.Completed)
            second as AudioDiscoveryResult.Completed
            assertEquals(
                "Repeated discovery must preserve the same provider document set without duplicates",
                first.files.map { it.documentId }.toSet(),
                second.files.map { it.documentId }.toSet(),
            )
            assertEquals(second.files.size, second.files.map { it.documentId }.toSet().size)
            Log.i(TAG, "PASS|sourceId=${source.sourceId}|files=${first.files.size}|root=$TEST_ROOT_NAME")
    }

    private fun findSavedSourceReadOnly(context: Context, sourceId: String): LibrarySourceEntity? {
        val databaseFile = context.getDatabasePath(CatalogDatabaseFactory.DATABASE_NAME)
        if (!databaseFile.isFile) return null
        return SQLiteDatabase.openDatabase(
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
                    "volume_name",
                    "persisted_permission_flags",
                    "permission_state",
                    "availability",
                    "created_at_epoch_ms",
                    "updated_at_epoch_ms",
                    "selection_state",
                ),
                "source_id = ?",
                arrayOf(sourceId),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                LibrarySourceEntity(
                    sourceId = cursor.getString(cursor.getColumnIndexOrThrow("source_id")),
                    sourceType = LibrarySourceType.valueOf(
                        cursor.getString(cursor.getColumnIndexOrThrow("source_type")),
                    ),
                    rootUri = cursor.getString(cursor.getColumnIndexOrThrow("root_uri")),
                    volumeName = cursor.getString(cursor.getColumnIndexOrThrow("volume_name")),
                    persistedPermissionFlags = cursor.getInt(
                        cursor.getColumnIndexOrThrow("persisted_permission_flags"),
                    ),
                    permissionState = SourcePermissionState.valueOf(
                        cursor.getString(cursor.getColumnIndexOrThrow("permission_state")),
                    ),
                    availability = SourceAvailability.valueOf(
                        cursor.getString(cursor.getColumnIndexOrThrow("availability")),
                    ),
                    createdAtEpochMs = cursor.getLong(
                        cursor.getColumnIndexOrThrow("created_at_epoch_ms"),
                    ),
                    updatedAtEpochMs = cursor.getLong(
                        cursor.getColumnIndexOrThrow("updated_at_epoch_ms"),
                    ),
                    selectionState = SourceSelectionState.valueOf(
                        cursor.getString(cursor.getColumnIndexOrThrow("selection_state")),
                    ),
                )
            }
        }
    }

    private fun rootDisplayName(context: Context, rootUri: String): String? = try {
        val tree = Uri.parse(rootUri)
        val document = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        context.contentResolver.query(
            document,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun assume(enabled: Boolean, reason: String) {
        if (!enabled) Log.i(TAG, "SKIP|$reason")
        assumeTrue(reason, enabled)
    }

    private fun requireReadable(result: DirectoryReadResult, location: String): Readable = when (result) {
        is Readable -> result
        DirectoryReadResult.PermissionLost -> skip("Persisted permission was lost while listing $location")
        DirectoryReadResult.Unavailable -> skip("SAF provider is unavailable while listing $location")
        DirectoryReadResult.Unreadable -> error("Selected test data is unreadable at $location")
        DirectoryReadResult.Failed -> error("SAF listing failed at $location")
    }

    private fun skip(reason: String): Nothing {
        Log.i(TAG, "SKIP|$reason")
        assumeTrue(reason, false)
        error("JUnit assumption unexpectedly returned")
    }

    private companion object {
        const val TAG = "SmoothMediaRealSafDiscovery"
        const val ARG_ENABLED = "enableRealSafDiscovery"
        const val ARG_SOURCE_ID = "testSafSourceId"
        const val TEST_ROOT_NAME = "SmoothMediaSafDiscoveryTestRoot"
        const val ROOT_MP3_NAME = "P01-04B-root.mp3"
        const val NESTED_DIRECTORY_NAME = "nested"
        const val NESTED_FLAC_NAME = "P01-04B-nested.flac"
    }
}
