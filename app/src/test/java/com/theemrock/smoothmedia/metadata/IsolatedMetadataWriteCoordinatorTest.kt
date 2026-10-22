package com.theemrock.smoothmedia.metadata

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsolatedMetadataWriteCoordinatorTest {
    @Test
    fun successfulMutationAndInjectedCommitKeepBackupAndChangeOnlySourceAtCommit() = withFixture { source, workspace ->
        val coordinator = IsolatedMetadataWriteCoordinator(workspace)
        val session = prepared(coordinator, source)
        val mutation = coordinator.mutate(session, { path -> write(path, "new-tags"); true }, { read(it) == "new-tags" })
        assertTrue(mutation is IsolatedWriteMutationResult.ReadyToCommit)
        assertEquals("original", read(source))
        assertEquals(IsolatedWriteCommitResult.Committed, coordinator.commit(session) { target, staged -> Files.copy(staged, target, StandardCopyOption.REPLACE_EXISTING); true })
        assertEquals("new-tags", read(source))
        assertTrue(Files.exists(session.backup))
    }

    @Test
    fun preflightAndWriteFailureAreFailClosed() = withFixture { source, workspace ->
        val coordinator = IsolatedMetadataWriteCoordinator(workspace)
        assertEquals(IsolatedWritePreparationRejection.INVALID_SOURCE_ID, (coordinator.prepare(source, " ") as IsolatedWritePreparationResult.Rejected).reason)
        val session = prepared(coordinator, source)
        val failure = coordinator.mutate(session, { false }, { true }) as IsolatedWriteMutationResult.Rejected
        assertEquals(IsolatedWriteMutationRejection.WRITE_FAILED, failure.reason)
        assertEquals("original", read(source))
        assertTrue(Files.exists(session.backup))
    }

    @Test
    fun partialWriteValidationMismatchAndCancellationRetainBackup() = withFixture { source, workspace ->
        val coordinator = IsolatedMetadataWriteCoordinator(workspace)
        val partial = prepared(coordinator, source)
        val partialResult = coordinator.mutate(partial, { path -> write(path, "partial"); true }, { false }) as IsolatedWriteMutationResult.Rejected
        assertEquals(IsolatedWriteMutationRejection.VALIDATION_FAILED, partialResult.reason)
        assertTrue(Files.exists(partial.backup))

        val cancelled = prepared(coordinator, source)
        val cancelledResult = coordinator.mutate(cancelled, { true }, { true }) { true } as IsolatedWriteMutationResult.Rejected
        assertEquals(IsolatedWriteMutationRejection.CANCELLED, cancelledResult.reason)
        assertTrue(Files.exists(cancelled.backup))
        assertEquals("original", read(source))
    }

    @Test
    fun sourceHashMismatchBlocksMutationAndCommit() = withFixture { source, workspace ->
        val coordinator = IsolatedMetadataWriteCoordinator(workspace)
        val session = prepared(coordinator, source)
        write(source, "external-change")
        val mutation = coordinator.mutate(session, { true }, { true }) as IsolatedWriteMutationResult.Rejected
        assertEquals(IsolatedWriteMutationRejection.SOURCE_CHANGED_BEFORE_WRITE, mutation.reason)
        assertEquals(IsolatedWriteCommitRejection.NOT_READY, (coordinator.commit(session) { _, _ -> true } as IsolatedWriteCommitResult.Failed).reason)
        assertTrue(Files.exists(session.backup))
    }

    @Test
    fun unusableSpaceMeasurementRemainsFailClosed() = withFixture { source, workspace ->
        val coordinator = IsolatedMetadataWriteCoordinator(workspace, usableSpaceBytes = { 0L })
        val rejected = coordinator.prepare(source, "track-1") as IsolatedWritePreparationResult.Rejected
        assertEquals(IsolatedWritePreparationRejection.INSUFFICIENT_SPACE, rejected.reason)
    }

    @Test
    fun rollbackSuccessRestoresSourceAndRollbackFailurePreservesRecoveryBackup() = withFixture { source, workspace ->
        val coordinator = IsolatedMetadataWriteCoordinator(workspace)
        val session = prepared(coordinator, source)
        write(source, "bad-commit")
        assertEquals(IsolatedWriteRollbackResult.RolledBack, coordinator.rollback(session) { backup, target -> Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING); true })
        assertEquals("original", read(source))
        write(source, "still-bad")
        val failed = coordinator.rollback(session) { _, _ -> false } as IsolatedWriteRollbackResult.Failed
        assertEquals(session.backup, failed.recoveryBackup)
        assertTrue(Files.exists(failed.recoveryBackup))
        assertEquals("still-bad", read(source))
    }

    private fun prepared(coordinator: IsolatedMetadataWriteCoordinator, source: Path): IsolatedWriteSession =
        (coordinator.prepare(source, "track-1") as IsolatedWritePreparationResult.Prepared).session

    private fun withFixture(block: (Path, Path) -> Unit) {
        val root = Files.createTempDirectory("smooth-id3-10-")
        val source = root.resolve("source.flac")
        val workspace = root.resolve("staging")
        Files.createDirectories(workspace)
        write(source, "original")
        try {
            block(source, workspace)
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path), Charsets.UTF_8)
    private fun write(path: Path, value: String) { Files.write(path, value.toByteArray(Charsets.UTF_8)) }
}
