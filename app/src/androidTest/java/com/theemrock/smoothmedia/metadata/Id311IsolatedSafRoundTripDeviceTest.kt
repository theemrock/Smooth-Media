package com.theemrock.smoothmedia.metadata

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.StatFs
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Id311IsolatedSafRoundTripDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val testContext = instrumentation.context
    private val targetContext = instrumentation.targetContext
    private val resolver: ContentResolver = testContext.contentResolver
    private val adapter = KyantTagLibAdapter()
    private val parser = FlacMetadataParser()
    private lateinit var fixtureUri: android.net.Uri
    private lateinit var sourceMirror: File
    private lateinit var workspace: File
    private lateinit var originalBytes: ByteArray

    @Before
    fun setUpOwnedProviderFixture() {
        val provider = testContext.packageManager.resolveContentProvider(PROVIDER_AUTHORITY, 0)
        assertTrue("Fixture provider missing", provider != null)
        assertEquals("Fixture provider must belong to test APK", testContext.packageName, provider!!.packageName)
        val asset = testContext.assets.open(FIXTURE_ASSET).use { it.readBytes() }
        assertTrue(asset.isNotEmpty())
        val name = "id3-11-${System.nanoTime()}.flac"
        fixtureUri = Uri.parse("content://$PROVIDER_AUTHORITY/$name")
        assertEquals(PROVIDER_AUTHORITY, fixtureUri.authority)
        resolver.call(fixtureUri, "create", name, Bundle().apply { putByteArray("bytes", asset) })
            ?: error("Test provider did not create fixture")
        originalBytes = readProviderBytes()
        sourceMirror = File(targetContext.cacheDir, "$name.mirror")
        workspace = File(targetContext.cacheDir, "$name.workspace")
        workspace.mkdirs()
        sourceMirror.writeBytes(originalBytes)
    }

    @After
    fun tearDownOwnedProviderFixture() {
        runCatching { resolver.call(fixtureUri, "delete", fixtureUri.lastPathSegment, null) }
        sourceMirror.delete()
        workspace.deleteRecursively()
    }

    @Test
    fun nativeArtistCommitThroughOwnedPfdPreservesAudioAndPictures() {
        val before = inspection(sourceMirror)
        val coordinator = coordinator()
        val session = prepared(coordinator)
        val mutation = coordinator.mutate(
            session = session,
            writer = { staged ->
                val snapshot = readSnapshot(staged.toFile())
                nativeWrite(staged, FileTagChange(snapshot.properties + (ARTIST_KEY to listOf(UPDATED_ARTIST)), pictures = null))
            },
            validator = { staged ->
                val after = inspection(staged.toFile())
                MetadataArtifactVerification.compare(MetadataArtifactVerification.summarize(before), MetadataArtifactVerification.summarize(after)) == FlacVerificationResult.Preserved &&
                    readSnapshot(staged.toFile()).properties[ARTIST_KEY]?.single() == UPDATED_ARTIST
            },
        )
        assertTrue(mutation is IsolatedWriteMutationResult.ReadyToCommit)
        val commit = coordinator.commit(session) { _, staged ->
            val current = readProviderBytes()
            if (!current.contentEquals(originalBytes)) return@commit false
            writeProviderBytes(Files.readAllBytes(staged))
            sourceMirror.writeBytes(readProviderBytes())
            readProviderBytes().contentEquals(Files.readAllBytes(staged))
        }
        assertEquals(IsolatedWriteCommitResult.Committed, commit)
        val after = inspection(sourceMirror)
        assertEquals(FlacVerificationResult.Preserved, MetadataArtifactVerification.compare(MetadataArtifactVerification.summarize(before), MetadataArtifactVerification.summarize(after)))
        assertEquals(UPDATED_ARTIST, readSnapshot(sourceMirror).properties[ARTIST_KEY]?.single())
        assertTrue(readProviderBytes().contentEquals(sourceMirror.readBytes()))
    }

    @Test
    fun noOpCommitAndReadBackPreserveAllIndependentHashes() {
        val before = inspection(sourceMirror)
        val coordinator = coordinator()
        val session = prepared(coordinator)
        val mutation = coordinator.mutate(session, { staged -> nativeWrite(staged, FileTagChange(readSnapshot(staged.toFile()).properties, pictures = null)) }, { staged ->
            MetadataArtifactVerification.compare(MetadataArtifactVerification.summarize(before), MetadataArtifactVerification.summarize(inspection(staged.toFile()))) == FlacVerificationResult.Preserved
        })
        assertTrue(mutation is IsolatedWriteMutationResult.ReadyToCommit)
        assertEquals(IsolatedWriteCommitResult.Committed, coordinator.commit(session) { _, staged ->
            writeProviderBytes(Files.readAllBytes(staged))
            sourceMirror.writeBytes(readProviderBytes())
            readProviderBytes().contentEquals(Files.readAllBytes(staged))
        })
        assertEquals(FlacVerificationResult.Preserved, MetadataArtifactVerification.compare(MetadataArtifactVerification.summarize(before), MetadataArtifactVerification.summarize(inspection(sourceMirror))))
    }

    @Test
    fun providerSourceChangeBlocksCommitAndKeepsBackup() {
        val coordinator = coordinator()
        val session = prepared(coordinator)
        val ready = coordinator.mutate(session, { true }, { true })
        assertTrue(ready is IsolatedWriteMutationResult.ReadyToCommit)
        writeProviderBytes(originalBytes + byteArrayOf(0x55))
        val result = coordinator.commit(session) { _, _ ->
            if (!readProviderBytes().contentEquals(originalBytes)) return@commit false
            true
        }
        assertTrue(result is IsolatedWriteCommitResult.Failed)
        assertTrue(Files.exists(session.backup))
        assertTrue(readProviderBytes().contentEquals(originalBytes + byteArrayOf(0x55)))
    }

    @Test
    fun rollbackUsesProviderPfdAndFailureRetainsBackup() {
        val coordinator = coordinator()
        val session = prepared(coordinator)
        writeProviderBytes(originalBytes + byteArrayOf(0x66))
        val restored = coordinator.rollback(session) { backup, _ -> writeProviderBytes(Files.readAllBytes(backup)); sourceMirror.writeBytes(readProviderBytes()); readProviderBytes().contentEquals(originalBytes) }
        assertEquals(IsolatedWriteRollbackResult.RolledBack, restored)
        assertTrue(readProviderBytes().contentEquals(originalBytes))
        writeProviderBytes(originalBytes + byteArrayOf(0x77))
        val failed = coordinator.rollback(session) { _, _ -> false } as IsolatedWriteRollbackResult.Failed
        assertTrue(Files.exists(failed.recoveryBackup))
        assertTrue(readProviderBytes().contentEquals(originalBytes + byteArrayOf(0x77)))
    }

    @Test
    fun partialWriteAndPostCommitMismatchAreExplicitSimulations() {
        val coordinator = coordinator()
        val partialSession = prepared(coordinator)
        val partial = coordinator.mutate(partialSession, { staged -> Files.write(staged, byteArrayOf(1, 2, 3)); true }, { false }) as IsolatedWriteMutationResult.Rejected
        assertEquals(IsolatedWriteMutationRejection.VALIDATION_FAILED, partial.reason)
        assertTrue(Files.exists(partialSession.backup))

        val mismatchSession = prepared(coordinator)
        assertTrue(coordinator.mutate(mismatchSession, { true }, { true }) is IsolatedWriteMutationResult.ReadyToCommit)
        val mismatch = coordinator.commit(mismatchSession) { _, _ -> sourceMirror.writeBytes(byteArrayOf(9)); true } as IsolatedWriteCommitResult.Failed
        assertEquals(IsolatedWriteCommitRejection.POST_COMMIT_MISMATCH, mismatch.reason)
        assertTrue(Files.exists(mismatchSession.backup))
    }

    private fun prepared(coordinator: IsolatedMetadataWriteCoordinator): IsolatedWriteSession {
        return when (val result = coordinator.prepare(sourceMirror.toPath(), "id3-11")) {
            is IsolatedWritePreparationResult.Prepared -> result.session
            is IsolatedWritePreparationResult.Rejected -> {
                throw AssertionError(
                    "ID3-11 preparation rejected: reason=${result.reason}, " +
                        "source=${sourceMirror.absolutePath}, sourceExists=${sourceMirror.isFile}, " +
                        "sourceBytes=${sourceMirror.length()}, workspace=${workspace.absolutePath}, " +
                        "workspaceExists=${workspace.isDirectory}",
                )
            }
        }
    }

    private fun coordinator(): IsolatedMetadataWriteCoordinator =
        IsolatedMetadataWriteCoordinator(workspace.toPath(), usableSpaceBytes = { path ->
            StatFs(path.toString()).availableBytes
        })

    private fun inspection(file: File): FlacInspection =
        (parser.parse(file.toPath()) as FlacParseResult.Success).inspection

    private fun readSnapshot(file: File): FileTagSnapshot =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { adapter.read(it).getOrThrow() }

    private fun nativeWrite(file: java.nio.file.Path, change: FileTagChange): Boolean =
        ParcelFileDescriptor.open(file.toFile(), ParcelFileDescriptor.MODE_READ_WRITE).use { adapter.write(it, change) is FileTagWriteResult.Written }

    private fun readProviderBytes(): ByteArray =
        resolver.openFileDescriptor(fixtureUri, "r")!!.use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }

    private fun writeProviderBytes(bytes: ByteArray) {
        resolver.openFileDescriptor(fixtureUri, "rw")!!.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).use { output ->
                output.channel.truncate(0)
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
        }
    }

    private companion object {
        const val FIXTURE_ASSET = "id3_native_roundtrip_fixture.flac"
        const val PROVIDER_AUTHORITY = "com.theemrock.smoothmedia.test.fixture"
        const val ARTIST_KEY = "ARTIST"
        const val UPDATED_ARTIST = "ID3-11 SAF Artist"
    }
}
