package com.theemrock.smoothmedia.metadata

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealSafMetadataPilotTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private val adapter = KyantTagLibAdapter()

    @Test
    fun writesArtistCodexToAllRootMp3AndFlacFiles() {
        val source = findSelectedSource()
        val root = Uri.parse(source.rootUri)
        assertEquals("com.android.externalstorage.documents", root.authority)
        val treeDocumentId = DocumentsContract.getTreeDocumentId(root)
        assertEquals("primary:Download/Barış Manço", treeDocumentId)
        assertTrue(source.persistedPermissionFlags and 3 == 3)
        assertTrue(resolver.persistedUriPermissions.any { it.uri == root && it.isReadPermission && it.isWritePermission })

        val rootDocument = DocumentsContract.buildDocumentUriUsingTree(root, treeDocumentId)
        val rootName = resolver.query(
            rootDocument,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
        }
        assertEquals("Barış Manço", rootName)

        val children: List<Child> = resolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(root, treeDocumentId),
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            buildList<Child> {
                val id = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val name = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val size = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val mime = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    add(Child(cursor.getString(id), cursor.getString(name), cursor.getLong(size), cursor.getString(mime)))
                }
            }
        } ?: error("SAF provider returned no child cursor")

        val files = children.filter { child ->
            child.mime != DocumentsContract.Document.MIME_TYPE_DIR &&
                (child.name.endsWith(".mp3", ignoreCase = true) || child.name.endsWith(".flac", ignoreCase = true))
        }.sortedBy { it.id }
        assertTrue("No root MP3/FLAC files found", files.isNotEmpty())
        assertTrue(files.all { it.id.startsWith("primary:Download/Barış Manço/") })
        Log.i(TAG, "ROOT_FILES|count=${files.size}|mp3=${files.count { it.format() == AudioFormat.MP3 }}|flac=${files.count { it.format() == AudioFormat.FLAC }}")

        val snapshots = files.map { child ->
            val uri = DocumentsContract.buildDocumentUriUsingTree(root, child.id)
            val bytes = readBytes(uri)
            val before = read(uri)
            val beforeHash = hash(uri)
            assertEquals(child.size, bytes.size.toLong())
            Log.i(TAG, "BEFORE|format=${child.format()}|documentId=${child.id}|name=${child.name}|size=${child.size}|hash=${beforeHash.digest}|artist=${before.properties["ARTIST"]}|fields=${before.properties.keys.sorted()}|artwork=${before.pictures.size}")
            PilotFile(child, uri, before, beforeHash)
        }

        snapshots.forEachIndexed { index, file ->
            writeAndVerify(file)
            Log.i(TAG, "PROGRESS|completed=${index + 1}|total=${snapshots.size}")
        }
    }

    private fun writeAndVerify(file: PilotFile) {
        val child = file.child
        val uri = file.uri
        val before = file.before
        val beforeHash = file.beforeHash
        val format = child.format()
        val result = MetadataFileWriteSession(resolver, adapter) { locator -> hash(Uri.parse(locator)) }.write(
            MetadataFileWriteTarget(
                trackId = "real-pilot-${format.name}",
                locationId = "real-pilot-${format.name}",
                sourceId = "real-pilot",
                locator = uri.toString(),
                format = format,
                locationAvailability = LocationAvailability.AVAILABLE,
                sourceAvailability = SourceAvailability.AVAILABLE,
                sourcePermission = SourcePermissionState.GRANTED,
                sourceSelection = SourceSelectionState.ACTIVE,
                excluded = false,
                currentHash = beforeHash,
            ),
            FileTagEditMapper.toChange(before, FileTagEdit(artist = "CODEX")),
        )
        assertTrue("Write failed for ${child.name}: $result", result is MetadataFileWriteOutcome.Written)
        val written = result as MetadataFileWriteOutcome.Written
        val afterBytes = readBytes(uri)
        val after = read(uri)
        val afterHash = hash(uri)
        if (before.properties["ARTIST"] != listOf("CODEX")) {
            assertNotEquals(beforeHash.digest, afterHash.digest)
        }
        assertEquals(child.size, afterBytes.size.toLong())
        assertEquals(listOf("CODEX"), after.properties["ARTIST"])
        assertPreserved(before, after, "ARTIST")
        assertEquals(written.currentHash.digest, afterHash.digest)
        Log.i(TAG, "AFTER|format=$format|documentId=${child.id}|name=${child.name}|size=${afterBytes.size}|hash=${afterHash.digest}|artist=${after.properties["ARTIST"]}|fields=${after.properties.keys.sorted()}|artwork=${after.pictures.size}|audioContentIndependentCheck=SKIP")
    }

    private fun findSelectedSource(): LibrarySourceEntity {
        val database = SQLiteDatabase.openDatabase(
            context.getDatabasePath("smooth-media-catalog.db").absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        return database.use {
            it.query(
                "library_sources",
                arrayOf("source_id", "source_type", "root_uri", "volume_name", "persisted_permission_flags", "permission_state", "availability", "created_at_epoch_ms", "updated_at_epoch_ms", "selection_state"),
                "source_type = ? AND selection_state = ?",
                arrayOf(LibrarySourceType.SAF_TREE.name, SourceSelectionState.ACTIVE.name),
                null,
                null,
                "updated_at_epoch_ms DESC",
                "1",
            ).use { cursor ->
                assumeTrue("No active p09test SAF source selected", cursor.moveToFirst())
                LibrarySourceEntity(
                    sourceId = cursor.getString(0),
                    sourceType = LibrarySourceType.valueOf(cursor.getString(1)),
                    rootUri = cursor.getString(2),
                    volumeName = cursor.getString(3),
                    persistedPermissionFlags = cursor.getInt(4),
                    permissionState = SourcePermissionState.valueOf(cursor.getString(5)),
                    availability = SourceAvailability.valueOf(cursor.getString(6)),
                    createdAtEpochMs = cursor.getLong(7),
                    updatedAtEpochMs = cursor.getLong(8),
                    selectionState = SourceSelectionState.valueOf(cursor.getString(9)),
                )
            }
        }
    }

    private fun read(uri: Uri): FileTagSnapshot = resolver.openFileDescriptor(uri, "r")!!.use { adapter.read(it).getOrThrow() }

    private fun readBytes(uri: Uri): ByteArray = resolver.openFileDescriptor(uri, "r")!!.use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun hash(uri: Uri): StoredFileHash = SafMetadataHashProvider(resolver)(uri.toString())!!

    private fun assertPreserved(before: FileTagSnapshot, after: FileTagSnapshot, changedKey: String) {
        assertEquals(before.properties.filterKeys { it != changedKey }, after.properties.filterKeys { it != changedKey })
        assertEquals(before.pictures, after.pictures)
    }

    private data class Child(val id: String, val name: String, val size: Long, val mime: String)
    private data class PilotFile(
        val child: Child,
        val uri: Uri,
        val before: FileTagSnapshot,
        val beforeHash: StoredFileHash,
    )

    private fun Child.format(): AudioFormat = when {
        name.endsWith(".mp3", ignoreCase = true) -> AudioFormat.MP3
        name.endsWith(".flac", ignoreCase = true) -> AudioFormat.FLAC
        else -> error("Unsupported pilot file: $name")
    }

    private companion object {
        const val TAG = "SmoothMediaRealSafMetadataPilot"
    }
}
