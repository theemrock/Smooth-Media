package com.theemrock.smoothmedia.metadata

import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafMetadataWriteAdapterTest {
    @Test
    fun blockedGateNeverProducesCommittedOrReconciledResult() = withFixture { source, workspace ->
        val adapter = adapter(workspace, gate = MetadataEditorWriteGate())
        val result = adapter.stage(request(source, workspace), validator = { _, _ -> true })
        assertEquals(SafMetadataWriteResult.Blocked, result)
    }

    @Test
    fun staleHashIsRejectedBeforeStaging() = withFixture { source, workspace ->
        val adapter = adapter(workspace, gate = MetadataEditorWriteGate(true))
        val result = adapter.stage(request(source, workspace, observedHash = StoredFileHash("SHA-256", 1, "stale")), validator = { _, _ -> true })
        assertEquals(SafMetadataWriteRejection.STALE_HASH, (result as SafMetadataWriteResult.Rejected).reason)
    }

    @Test
    fun validStageChangesOnlyRequestedFieldAndPreservesBackup() = withFixture { source, workspace ->
        val adapter = adapter(workspace, gate = MetadataEditorWriteGate(true))
        val result = adapter.stage(request(source, workspace), validator = { before, after ->
            before.pictures == after.pictures && after.properties["ARTIST"] == listOf("Yeni")
        }) as SafMetadataWriteResult.Prepared
        assertEquals(listOf("Eski"), result.before.properties["ARTIST"])
        assertEquals(listOf("Yeni"), result.after.properties["ARTIST"])
        assertTrue(Files.exists(result.session.backup))
    }

    @Test
    fun unsupportedProviderCapabilitiesRejectCommitAndKeepBackup() = withFixture { source, workspace ->
        val adapter = adapter(workspace, gate = MetadataEditorWriteGate(true))
        val prepared = adapter.stage(request(source, workspace), validator = { _, _ -> true }) as SafMetadataWriteResult.Prepared
        val result = adapter.commit(prepared, SafMetadataWriteCapabilities(false, true, true, true)) { _, _ -> true }
        assertEquals(SafMetadataWriteRejection.PROVIDER_CAPABILITY_UNVERIFIED, (result as SafMetadataWriteResult.Rejected).reason)
        assertTrue(Files.exists(prepared.session.backup))
    }

    @Test
    fun verifiedCapabilitiesUseOnlyInjectedCommitCallback() = withFixture { source, workspace ->
        val adapter = adapter(workspace, gate = MetadataEditorWriteGate(true))
        val prepared = adapter.stage(request(source, workspace), validator = { _, _ -> true }) as SafMetadataWriteResult.Prepared
        val result = adapter.commit(prepared, SafMetadataWriteCapabilities(true, true, true, true)) { target, staged ->
            Files.copy(staged, target, StandardCopyOption.REPLACE_EXISTING)
            true
        }
        assertEquals(SafMetadataWriteResult.Committed, result)
    }

    @Test
    fun rollbackSuccessRestoresThroughInjectedCallback() = withFixture { source, workspace ->
        val adapter = adapter(workspace, gate = MetadataEditorWriteGate(true))
        val prepared = adapter.stage(request(source, workspace), validator = { _, _ -> true }) as SafMetadataWriteResult.Prepared
        Files.write(source, byteArrayOf(9, 9, 9))
        val result = adapter.rollback(prepared) { backup, target ->
            Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING)
            true
        }
        assertTrue(result is SafMetadataWriteResult.Prepared)
        assertTrue(Files.readAllBytes(source).contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun rollbackFailureRetainsRecoveryBackup() = withFixture { source, workspace ->
        val adapter = adapter(workspace, gate = MetadataEditorWriteGate(true))
        val prepared = adapter.stage(request(source, workspace), validator = { _, _ -> true }) as SafMetadataWriteResult.Prepared
        val result = adapter.rollback(prepared) { _, _ -> false }
        assertEquals(SafMetadataWriteRejection.ROLLBACK_FAILED, (result as SafMetadataWriteResult.Rejected).reason)
        assertTrue(Files.exists(prepared.session.backup))
    }

    private fun adapter(workspace: Path, gate: MetadataEditorWriteGate): SafMetadataWriteAdapter =
        SafMetadataWriteAdapter(gate, FakePathTagAdapter())

    private fun request(source: Path, workspace: Path, observedHash: StoredFileHash = HASH): SafMetadataWriteRequest =
        SafMetadataWriteRequest(
            target = MetadataFileWriteTarget(
                trackId = "track",
                locationId = "location",
                sourceId = "source",
                locator = source.toString(),
                format = AudioFormat.FLAC,
                locationAvailability = LocationAvailability.AVAILABLE,
                sourceAvailability = SourceAvailability.AVAILABLE,
                sourcePermission = SourcePermissionState.GRANTED,
                sourceSelection = SourceSelectionState.ACTIVE,
                excluded = false,
                currentHash = observedHash,
            ),
            source = source,
            workspace = workspace,
            change = FileTagChange(mapOf("ARTIST" to listOf("Yeni")), null),
            observedHash = HASH,
        )

    private fun withFixture(block: (Path, Path) -> Unit) {
        val root = Files.createTempDirectory("id3-12-")
        val source = root.resolve("source.flac")
        val workspace = root.resolve("workspace")
        Files.createDirectories(workspace)
        Files.write(source, byteArrayOf(1, 2, 3))
        try {
            block(source, workspace)
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private class FakePathTagAdapter : PathTagAdapter {
        override fun read(path: Path): FileTagSnapshot = FileTagSnapshot(
            properties = mapOf("ARTIST" to listOf(if (path.fileName.toString().contains("stage")) "Yeni" else "Eski")),
            pictures = listOf(FileTagPicture(byteArrayOf(9), "image/png", "cover", "front")),
        )

        override fun write(path: Path, change: FileTagChange): FileTagWriteResult =
            FileTagWriteResult.Written(FileTagSnapshot(change.properties, read(path).pictures))
    }

    private companion object {
        val HASH = StoredFileHash("SHA-256", 1, "hash")
    }
}
