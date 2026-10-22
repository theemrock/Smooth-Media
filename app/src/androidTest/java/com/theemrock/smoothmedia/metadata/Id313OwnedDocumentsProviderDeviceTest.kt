package com.theemrock.smoothmedia.metadata

import android.content.ContentResolver
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Id313OwnedDocumentsProviderDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val testContext = instrumentation.context
    private val targetContext = instrumentation.targetContext
    private val resolver: ContentResolver = testContext.contentResolver
    private val adapter = KyantTagLibAdapter()
    private val parser = FlacMetadataParser()
    private lateinit var sourceUri: android.net.Uri
    private lateinit var sourceMirror: File
    private lateinit var workspace: File
    private lateinit var originalBytes: ByteArray

    @Before
    fun createOnlyTestOwnedDocument() {
        val provider = testContext.packageManager.resolveContentProvider(AUTHORITY, 0)
        assertNotNull("Owned DocumentsProvider missing", provider)
        assertEquals("Provider must belong to the p09test APK", targetContext.packageName, provider!!.packageName)
        android.util.Log.i(TAG, "ID3_13_CONTEXT|instrumentationPackage=${testContext.packageName}|targetPackage=${targetContext.packageName}|instrumentationUid=${testContext.applicationInfo.uid}|targetUid=${targetContext.applicationInfo.uid}|providerUid=${provider.applicationInfo.uid}")
        runCatching {
            resolver.query(DocumentsContract.buildRootsUri(AUTHORITY), null, null, null, null).use { cursor ->
                "count=${cursor?.count}|columns=${cursor?.columnNames?.joinToString(",")}"
            }
        }.onSuccess { android.util.Log.i(TAG, "ID3_13_ROOT_QUERY|result=$it") }
            .onFailure { android.util.Log.i(TAG, "ID3_13_ROOT_QUERY|result=${it::class.java.simpleName}|message=${it.message}") }
        val documentId = "id3-13-${System.nanoTime()}.flac"
        sourceUri = DocumentsContract.buildDocumentUri(AUTHORITY, documentId)
        val seedResult = resolver.call(
            sourceUri,
            OwnedDocumentsProvider.METHOD_SEED,
            documentId,
            Bundle().apply {
                putByteArray(OwnedDocumentsProvider.EXTRA_BYTES, testContext.assets.open(FIXTURE_ASSET).use { it.readBytes() })
            },
        ) ?: error("Owned provider did not seed fixture")
        assertEquals(targetContext.packageName, seedResult.getString(OwnedDocumentsProvider.EXTRA_PROVIDER_PACKAGE))
        assertEquals(provider.applicationInfo.uid, seedResult.getInt(OwnedDocumentsProvider.EXTRA_PROVIDER_UID))
        resolver.query(sourceUri, null, null, null, null).use { cursor ->
            assertTrue("Document query failed", cursor != null && cursor.count == 1)
        }
        val renamed = DocumentsContract.renameDocument(resolver, sourceUri, "id3-13-renamed-${System.nanoTime()}.flac")
        sourceUri = renamed ?: error("DocumentsProvider rename failed")
        originalBytes = readPfd(sourceUri)
        sourceMirror = File(targetContext.cacheDir, "id3-13-${System.nanoTime()}.mirror")
        workspace = File(targetContext.cacheDir, "id3-13-${System.nanoTime()}.workspace")
        workspace.mkdirs()
        sourceMirror.writeBytes(originalBytes)
        assertEquals(targetContext.cacheDir.canonicalPath, sourceMirror.parentFile?.canonicalPath)
        assertTrue((parser.parse(sourceMirror.toPath()) as? FlacParseResult.Success) != null)
    }

    @After
    fun deleteOnlyTestOwnedDocument() {
        if (::sourceUri.isInitialized) runCatching { DocumentsContract.deleteDocument(resolver, sourceUri) }
        if (::sourceMirror.isInitialized) sourceMirror.delete()
        if (::workspace.isInitialized) workspace.deleteRecursively()
    }

    @Test
    fun ownedProviderSupportsDocumentLifecycleAndRealDecoder() {
        val before = inspection(sourceMirror)
        assertEquals("Expected two PICTURE blocks", 2, before.pictures.size)
        val decoded = decode(sourceUri)
        assertTrue("Provider FLAC did not decode to PCM", decoded.pcmBytes > 0)
        assertTrue("Fixture duration is too short: ${decoded.durationUs}", decoded.durationUs >= 1_000_000L)
        android.util.Log.i(TAG, "ID3_13_PROVIDER|query=create=PASS|rename=PASS|open=rwt=PASS|decode=PASS|durationUs=${decoded.durationUs}")
    }

    @Test
    fun nativeArtistChangeThroughPfdPreservesAudioPicturesAndPersists() {
        val before = inspection(sourceMirror)
        val beforeSnapshot = readSnapshot(sourceMirror)
        val coordinator = coordinator()
        val session = prepared(coordinator)
        val mutation = coordinator.mutate(
            session,
            writer = { staged ->
                val snapshot = readSnapshot(staged.toFile())
                writeNative(staged.toFile(), FileTagChange(snapshot.properties + (ARTIST to listOf(UPDATED_ARTIST)), null))
            },
            validator = { staged ->
                val after = inspection(staged.toFile())
                before.pictures == after.pictures && before.audioSha256 == after.audioSha256 &&
                    readSnapshot(staged.toFile()).properties[ARTIST]?.single() == UPDATED_ARTIST
            },
        )
        assertTrue("Native staging failed: $mutation", mutation is IsolatedWriteMutationResult.ReadyToCommit)
        assertEquals(IsolatedWriteCommitResult.Committed, commitPfd(coordinator, session))
        val after = inspection(sourceMirror)
        assertEquals(before.pictures, after.pictures)
        assertEquals(before.audioSha256, after.audioSha256)
        assertEquals(UPDATED_ARTIST, readSnapshot(sourceMirror).properties[ARTIST]?.single())
        assertEquals(UPDATED_ARTIST, readSnapshotFromProvider().properties[ARTIST]?.single())
        assertTrue(decode(sourceUri).pcmBytes > 0)
        assertEquals(beforeSnapshot.pictures, readSnapshot(sourceMirror).pictures)
        android.util.Log.i(TAG, "ID3_13_ARTIST|native=PASS|artist=PASS|audio=PASS|pictures=PASS|decode=PASS|pfd=PASS")
    }

    @Test
    fun noOpRoundTripPreservesIndependentHashesAndPictures() {
        val before = inspection(sourceMirror)
        val snapshot = readSnapshot(sourceMirror)
        val coordinator = coordinator()
        val session = prepared(coordinator)
        assertTrue(coordinator.mutate(session, { staged -> writeNative(staged.toFile(), FileTagChange(snapshot.properties, null)) }, { true }) is IsolatedWriteMutationResult.ReadyToCommit)
        assertEquals(IsolatedWriteCommitResult.Committed, commitPfd(coordinator, session))
        val after = inspection(sourceMirror)
        assertEquals(before.audioSha256, after.audioSha256)
        assertEquals(before.pictures, after.pictures)
        assertTrue(decode(sourceUri).pcmBytes > 0)
        android.util.Log.i(TAG, "ID3_13_NOOP|audio=PASS|pictures=PASS|decode=PASS|fileHashEqual=${sha256(originalBytes) == sha256(readPfd(sourceUri))}")
    }

    @Test
    fun sourceChangeBlocksPfdCommitAndKeepsBackup() {
        val coordinator = coordinator()
        val session = prepared(coordinator)
        assertTrue(coordinator.mutate(session, { true }, { true }) is IsolatedWriteMutationResult.ReadyToCommit)
        writePfd(sourceUri, originalBytes + byteArrayOf(0x55), "rwt")
        val result = coordinator.commit(session) { _, _ ->
            if (!readPfd(sourceUri).contentEquals(originalBytes)) return@commit false
            true
        }
        assertTrue(result is IsolatedWriteCommitResult.Failed)
        assertTrue(Files.exists(session.backup))
        android.util.Log.i(TAG, "ID3_13_SOURCE_CHANGE|result=PASS|backupRetained=PASS|simulatedCommitGuard=PASS")
    }

    @Test
    fun shortTargetRollbackUsesTruncateAndUnsupportedRollbackIsFailClosed() {
        val coordinator = coordinator()
        val session = prepared(coordinator)
        assertTrue(coordinator.mutate(session, { true }, { true }) is IsolatedWriteMutationResult.ReadyToCommit)
        val longer = originalBytes + ByteArray(257) { 0x6A }
        writePfd(sourceUri, longer, "rwt")
        val restored = coordinator.rollback(session) { backup, _ ->
            writePfd(sourceUri, Files.readAllBytes(backup), "rwt")
            sourceMirror.writeBytes(readPfd(sourceUri))
            readPfd(sourceUri).contentEquals(originalBytes)
        }
        assertEquals(IsolatedWriteRollbackResult.RolledBack, restored)
        assertTrue(readPfd(sourceUri).contentEquals(originalBytes))

        writePfd(sourceUri, longer, "rwt")
        val unsupported = coordinator.rollback(session) { _, _ -> false }
        assertTrue(unsupported is IsolatedWriteRollbackResult.Failed)
        assertTrue(Files.exists((unsupported as IsolatedWriteRollbackResult.Failed).recoveryBackup))
        assertTrue(readPfd(sourceUri).contentEquals(longer))
        android.util.Log.i(TAG, "ID3_13_ROLLBACK|shortBackup=PASS|rwtTruncate=PASS|unsupportedProvider=FAIL_CLOSED|backupRetained=PASS|simulatedFailure=PASS")
    }

    private fun commitPfd(coordinator: IsolatedMetadataWriteCoordinator, session: IsolatedWriteSession): IsolatedWriteCommitResult =
        coordinator.commit(session) { _, staged ->
            if (!readPfd(sourceUri).contentEquals(originalBytes)) return@commit false
            writePfd(sourceUri, Files.readAllBytes(staged), "rwt")
            sourceMirror.writeBytes(readPfd(sourceUri))
            readPfd(sourceUri).contentEquals(Files.readAllBytes(staged))
        }

    private fun prepared(coordinator: IsolatedMetadataWriteCoordinator): IsolatedWriteSession =
        when (val result = coordinator.prepare(sourceMirror.toPath(), "id3-13")) {
            is IsolatedWritePreparationResult.Prepared -> result.session
            is IsolatedWritePreparationResult.Rejected -> error("Preparation rejected: ${result.reason}")
        }

    private fun coordinator() = IsolatedMetadataWriteCoordinator(workspace.toPath(), usableSpaceBytes = { path ->
        StatFs(path.toString()).availableBytes
    })

    private fun inspection(file: File): FlacInspection =
        (parser.parse(file.toPath()) as FlacParseResult.Success).inspection

    private fun readSnapshot(file: File): FileTagSnapshot =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { adapter.read(it).getOrThrow() }

    private fun readSnapshotFromProvider(): FileTagSnapshot =
        resolver.openFileDescriptor(sourceUri, "r")!!.use { adapter.read(it).getOrThrow() }

    private fun writeNative(file: File, change: FileTagChange): Boolean =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { adapter.write(it, change) is FileTagWriteResult.Written }

    private fun readPfd(uri: android.net.Uri): ByteArray =
        resolver.openFileDescriptor(uri, "r")!!.use { descriptor -> ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() } }

    private fun writePfd(uri: android.net.Uri, bytes: ByteArray, mode: String) {
        resolver.openFileDescriptor(uri, mode)!!.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun decode(uri: android.net.Uri): DecodeResult {
        val descriptor = resolver.openFileDescriptor(uri, "r")!!
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(descriptor.fileDescriptor)
            val index = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            assertNotNull("No audio track in fixture", index)
            extractor.selectTrack(index!!)
            val format = extractor.getTrackFormat(index)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var pcm = 0L
            val deadline = SystemClock.elapsedRealtime() + 20_000L
            while (!outputDone && SystemClock.elapsedRealtime() < deadline) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(input, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        pcm += info.size.toLong()
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            assertTrue("Decoder did not reach EOS", outputDone)
            return DecodeResult(format.getLong(MediaFormat.KEY_DURATION, 0L), pcm)
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
            descriptor.close()
        }
    }

    private data class DecodeResult(val durationUs: Long, val pcmBytes: Long)

    private companion object {
        const val AUTHORITY = "com.theemrock.smoothmedia.p09test.documents"
        const val FIXTURE_ASSET = "id3_native_roundtrip_fixture.flac"
        const val ARTIST = "ARTIST"
        const val UPDATED_ARTIST = "ID3-13 Owned Provider Artist"
        const val TIMEOUT_US = 10_000L
        const val TAG = "SmoothMediaId313"
    }
}
