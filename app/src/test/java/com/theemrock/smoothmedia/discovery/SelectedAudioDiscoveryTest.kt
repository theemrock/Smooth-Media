package com.theemrock.smoothmedia.discovery

import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.source.SelectedTree
import com.theemrock.smoothmedia.source.TreePermissionGateway
import com.theemrock.smoothmedia.source.TreePermissionInspection
import com.theemrock.smoothmedia.source.TreePermissionReleaseResult
import com.theemrock.smoothmedia.source.TreePermissionTakeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedAudioDiscoveryTest {
    @Test
    fun discoversOnlyMp3AndFlacRecursivelyWithFileInfoAndNoDuplicates() {
        val root = "content://provider/tree/music"
        val nested = "content://provider/tree/music/document/album"
        val mp3 = document("file-1", "content://provider/tree/music/document/file-1", "Track.MP3", "audio/mpeg", 123, 456)
        val flac = document("file-2", "content://provider/tree/music/document/album/file-2", "Track.flac", "audio/flac", 789, 987)
        val reader = FakeTreeReader(
            directories = mapOf(
                root to DirectoryReadResult.Readable(
                    listOf(
                        document("album", nested, "Album", DIRECTORY_MIME),
                        mp3,
                        mp3,
                        document("other", "content://provider/tree/music/document/other", "Track.ogg", "audio/ogg"),
                    ),
                ),
                nested to DirectoryReadResult.Readable(listOf(flac)),
            ),
        )

        val result = SelectedAudioDiscovery(FakePermissionGateway(), reader).discover(source(root))

        assertTrue(result is AudioDiscoveryResult.Completed)
        result as AudioDiscoveryResult.Completed
        assertEquals(2, result.files.size)
        val filesByDocumentId = result.files.associateBy { it.documentId }
        assertEquals(setOf("file-1", "file-2"), filesByDocumentId.keys)
        val mp3File = filesByDocumentId.getValue("file-1")
        assertEquals(AudioFormat.MP3, mp3File.format)
        assertEquals("content://provider/tree/music/document/file-1", mp3File.uri)
        assertEquals("source-1", mp3File.sourceId)
        assertEquals("Track.MP3", mp3File.displayName)
        assertEquals("audio/mpeg", mp3File.mimeType)
        assertEquals(123L, mp3File.sizeBytes)
        assertEquals(456L, mp3File.modifiedAtEpochMs)
        assertEquals(AudioFormat.FLAC, filesByDocumentId.getValue("file-2").format)
    }

    @Test
    fun repeatedDiscoveryReturnsUniqueFilesWithoutAccumulatingDuplicates() {
        val root = "content://provider/tree/music"
        val reader = FakeTreeReader(
            directories = mapOf(
                root to DirectoryReadResult.Readable(
                    listOf(
                        document("file-1", "content://provider/document/file-1", "Track.mp3", null),
                        document("file-1", "content://provider/document/file-1", "Track.mp3", null),
                    ),
                ),
            ),
        )
        val discovery = SelectedAudioDiscovery(FakePermissionGateway(), reader)

        val first = discovery.discover(source(root)) as AudioDiscoveryResult.Completed
        val second = discovery.discover(source(root)) as AudioDiscoveryResult.Completed

        assertEquals(1, first.files.size)
        assertEquals(first.files, second.files)
    }

    @Test
    fun recognizesSupportedMimeWithoutExtensionAndSkipsConflictingFormatClues() {
        val root = "content://provider/tree/music"
        val mp3ByMime = document("mp3", "content://provider/document/mp3", "track.bin", "audio/mpeg")
        val conflict = document("conflict", "content://provider/document/conflict", "track.mp3", "audio/flac")
        val reader = FakeTreeReader(
            directories = mapOf(root to DirectoryReadResult.Readable(listOf(mp3ByMime, conflict))),
        )

        val result = SelectedAudioDiscovery(FakePermissionGateway(), reader).discover(source(root))

        assertEquals(
            AudioDiscoveryResult.Completed(
                "source-1",
                listOf(mp3ByMime.toDiscoveryFile("source-1", AudioFormat.MP3)),
                0,
            ),
            result,
        )
    }

    @Test
    fun deeplyNestedSelectedTreeDoesNotDependOnCallStackDepth() {
        val root = "content://provider/tree/music"
        val directories = linkedMapOf<String, DirectoryReadResult>()
        var current = root
        repeat(6_000) { index ->
            val child = "content://provider/tree/music/document/directory-$index"
            directories[current] = DirectoryReadResult.Readable(
                listOf(document("directory-$index", child, "Folder", DIRECTORY_MIME)),
            )
            current = child
        }
        directories[current] = DirectoryReadResult.Readable(
            listOf(document("deep-file", "content://provider/document/deep-file", "deep.flac", null)),
        )

        val result = SelectedAudioDiscovery(
            FakePermissionGateway(),
            FakeTreeReader(directories = directories),
        ).discover(source(root))

        assertEquals(
            AudioDiscoveryResult.Completed(
                "source-1",
                listOf(document("deep-file", "content://provider/document/deep-file", "deep.flac", null)
                    .toDiscoveryFile("source-1", AudioFormat.FLAC)),
                0,
            ),
            result,
        )
    }

    @Test
    fun unreadableFileAndNestedFolderAreSkippedWithoutDroppingReadableFiles() {
        val root = "content://provider/tree/music"
        val blockedFolder = "content://provider/tree/music/document/blocked"
        val readable = document("ok", "content://provider/document/ok", "ok.flac", null)
        val unreadable = document("denied", "content://provider/document/denied", "denied.mp3", null)
        val reader = FakeTreeReader(
            directories = mapOf(
                root to DirectoryReadResult.Readable(
                    listOf(
                        readable,
                        unreadable,
                        document("blocked", blockedFolder, "Blocked", DIRECTORY_MIME),
                    ),
                ),
                blockedFolder to DirectoryReadResult.Unreadable,
            ),
            unreadableFiles = setOf(unreadable.uri),
        )

        val result = SelectedAudioDiscovery(FakePermissionGateway(), reader).discover(source(root))

        assertEquals(
            AudioDiscoveryResult.Completed("source-1", listOf(readable.toDiscoveryFile("source-1", AudioFormat.FLAC)), 2),
            result,
        )
    }

    @Test
    fun permissionLossIsReturnedSeparatelyAndStopsTraversal() {
        val root = "content://provider/tree/music"
        val reader = FakeTreeReader()
        val gateway = FakePermissionGateway(TreePermissionInspection.PermissionLost)

        val result = SelectedAudioDiscovery(gateway, reader).discover(source(root))

        assertEquals(AudioDiscoveryResult.PermissionLost("source-1"), result)
        assertTrue(reader.visitedDirectories.isEmpty())
    }

    @Test
    fun fileSpecificReadDenialIsSkippedWhileRevokedRootGrantIsReported() {
        val root = "content://provider/tree/music"
        val denied = document("denied", "content://provider/document/denied", "denied.mp3", null)
        val reader = FakeTreeReader(
            directories = mapOf(root to DirectoryReadResult.Readable(listOf(denied))),
            permissionDeniedFiles = setOf(denied.uri),
        )
        val stillAvailable = SelectedAudioDiscovery(FakePermissionGateway(), reader).discover(source(root))
        val revoked = SelectedAudioDiscovery(
            FakePermissionGateway(
                inspections = mutableListOf(
                    TreePermissionInspection.Available(1),
                    TreePermissionInspection.PermissionLost,
                ),
            ),
            reader,
        ).discover(source(root))

        assertEquals(AudioDiscoveryResult.Completed("source-1", emptyList(), 1), stillAvailable)
        assertEquals(AudioDiscoveryResult.PermissionLost("source-1"), revoked)
    }

    @Test
    fun unavailableRootAndInvalidPermissionCheckRemainDistinct() {
        val root = "content://provider/tree/music"
        val unavailableReader = FakeTreeReader(
            directories = mapOf(root to DirectoryReadResult.Unavailable),
        )
        val unavailable = SelectedAudioDiscovery(FakePermissionGateway(), unavailableReader).discover(source(root))
        val invalid = SelectedAudioDiscovery(
            FakePermissionGateway(TreePermissionInspection.InvalidUri),
            FakeTreeReader(),
        ).discover(source(root))

        assertEquals(AudioDiscoveryResult.SourceUnavailable("source-1"), unavailable)
        assertEquals(AudioDiscoveryResult.InvalidSource("source-1"), invalid)
    }

    @Test
    fun removedOrNonSafSourcesAreNeverTraversed() {
        val root = "content://provider/tree/music"
        val reader = FakeTreeReader()
        val discovery = SelectedAudioDiscovery(FakePermissionGateway(), reader)

        val removed = discovery.discover(source(root).copy(selectionState = SourceSelectionState.REMOVED))
        val mediaStore = discovery.discover(source(root).copy(sourceType = LibrarySourceType.MEDIA_STORE))

        assertEquals(AudioDiscoveryResult.NotSelected("source-1"), removed)
        assertEquals(AudioDiscoveryResult.UnsupportedSource("source-1"), mediaStore)
        assertTrue(reader.visitedDirectories.isEmpty())
    }

    private fun source(rootUri: String) = LibrarySourceEntity(
        sourceId = "source-1",
        sourceType = LibrarySourceType.SAF_TREE,
        rootUri = rootUri,
        volumeName = null,
        persistedPermissionFlags = 1,
        permissionState = SourcePermissionState.GRANTED,
        availability = SourceAvailability.AVAILABLE,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
        selectionState = SourceSelectionState.ACTIVE,
    )

    private fun document(
        documentId: String,
        uri: String,
        name: String,
        mimeType: String?,
        size: Long? = null,
        modified: Long? = null,
    ) = TreeDocument(documentId, uri = uri, displayName = name, mimeType = mimeType, isDirectory = mimeType == DIRECTORY_MIME, sizeBytes = size, modifiedAtEpochMs = modified)

    private fun TreeDocument.toDiscoveryFile(sourceId: String, format: AudioFormat) = DiscoveredAudioFile(
        sourceId = sourceId,
        uri = uri,
        documentId = documentId,
        displayName = displayName,
        format = format,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        modifiedAtEpochMs = modifiedAtEpochMs,
    )

    private companion object {
        const val DIRECTORY_MIME = "vnd.android.document/directory"
    }
}

private class FakeTreeReader(
    private val directories: Map<String, DirectoryReadResult> = emptyMap(),
    private val unreadableFiles: Set<String> = emptySet(),
    private val permissionDeniedFiles: Set<String> = emptySet(),
) : SafDocumentTreeReader {
    val visitedDirectories = mutableListOf<String>()

    override fun listChildren(directoryUri: String): DirectoryReadResult {
        visitedDirectories += directoryUri
        return directories[directoryUri] ?: DirectoryReadResult.Readable(emptyList())
    }

    override fun checkReadable(fileUri: String): DocumentReadability = when {
        fileUri in permissionDeniedFiles -> DocumentReadability.PERMISSION_LOST
        fileUri in unreadableFiles -> DocumentReadability.UNREADABLE
        else -> DocumentReadability.READABLE
    }
}

private class FakePermissionGateway(
    private val inspections: MutableList<TreePermissionInspection> = mutableListOf(TreePermissionInspection.Available(1)),
) : TreePermissionGateway {
    constructor(inspection: TreePermissionInspection) : this(mutableListOf(inspection))

    override fun takeAndVerify(selection: SelectedTree): TreePermissionTakeResult =
        TreePermissionTakeResult.Granted(1)

    override fun inspect(rootUri: String): TreePermissionInspection =
        if (inspections.size > 1) inspections.removeAt(0) else inspections.first()

    override fun release(rootUri: String, persistedPermissionFlags: Int): TreePermissionReleaseResult =
        TreePermissionReleaseResult.Released
}
