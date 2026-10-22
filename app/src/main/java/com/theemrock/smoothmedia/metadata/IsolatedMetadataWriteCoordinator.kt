package com.theemrock.smoothmedia.metadata

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

data class IsolatedWriteSession(
    val sourceId: String,
    val source: Path,
    val backup: Path,
    val staged: Path,
    val originalSha256: String,
    val originalSize: Long,
)

sealed interface IsolatedWritePreparationResult {
    data class Prepared(val session: IsolatedWriteSession) : IsolatedWritePreparationResult
    data class Rejected(val reason: IsolatedWritePreparationRejection) : IsolatedWritePreparationResult
}

enum class IsolatedWritePreparationRejection {
    INVALID_SOURCE_ID,
    SOURCE_NOT_REGULAR,
    WORKSPACE_NOT_DIRECTORY,
    SOURCE_INSIDE_WORKSPACE,
    INSUFFICIENT_SPACE,
    COPY_FAILED,
}

sealed interface IsolatedWriteMutationResult {
    data class ReadyToCommit(val session: IsolatedWriteSession) : IsolatedWriteMutationResult
    data class Rejected(val reason: IsolatedWriteMutationRejection, val recoveryBackup: Path?) : IsolatedWriteMutationResult
}

enum class IsolatedWriteMutationRejection {
    SOURCE_CHANGED_BEFORE_WRITE,
    CANCELLED,
    WRITE_FAILED,
    VALIDATION_FAILED,
    SOURCE_CHANGED_AFTER_WRITE,
}

sealed interface IsolatedWriteCommitResult {
    data object Committed : IsolatedWriteCommitResult
    data class Failed(val reason: IsolatedWriteCommitRejection, val recoveryBackup: Path) : IsolatedWriteCommitResult
}

enum class IsolatedWriteCommitRejection {
    NOT_READY,
    SOURCE_CHANGED,
    REPLACE_FAILED,
    POST_COMMIT_MISMATCH,
}

sealed interface IsolatedWriteRollbackResult {
    data object RolledBack : IsolatedWriteRollbackResult
    data class Failed(val recoveryBackup: Path) : IsolatedWriteRollbackResult
}

/**
 * File-only safety coordinator for isolated fixtures. It never opens SAF and never
 * replaces a production source by itself; commit and rollback are injected callbacks.
 */
class IsolatedMetadataWriteCoordinator(
    private val workspace: Path,
    private val usableSpaceBytes: (Path) -> Long = { path -> Files.getFileStore(path).usableSpace },
    private val sha256: (Path) -> String = ::sha256File,
) {
    private val readySessions = mutableSetOf<Path>()
    fun prepare(source: Path, sourceId: String): IsolatedWritePreparationResult {
        if (sourceId.isBlank()) return IsolatedWritePreparationResult.Rejected(IsolatedWritePreparationRejection.INVALID_SOURCE_ID)
        if (!Files.isRegularFile(source)) return IsolatedWritePreparationResult.Rejected(IsolatedWritePreparationRejection.SOURCE_NOT_REGULAR)
        if (!Files.isDirectory(workspace)) return IsolatedWritePreparationResult.Rejected(IsolatedWritePreparationRejection.WORKSPACE_NOT_DIRECTORY)
        val normalizedSource = source.toAbsolutePath().normalize()
        val normalizedWorkspace = workspace.toAbsolutePath().normalize()
        if (normalizedSource.startsWith(normalizedWorkspace)) return IsolatedWritePreparationResult.Rejected(IsolatedWritePreparationRejection.SOURCE_INSIDE_WORKSPACE)
        val size = Files.size(source)
        val usable = runCatching { usableSpaceBytes(workspace) }.getOrDefault(0L)
        if (size > usable / 2L) return IsolatedWritePreparationResult.Rejected(IsolatedWritePreparationRejection.INSUFFICIENT_SPACE)
        val backup = workspace.resolve("$sourceId-${UUID.randomUUID()}.backup")
        val staged = workspace.resolve("$sourceId-${UUID.randomUUID()}.stage")
        return runCatching {
            Files.copy(source, backup, StandardCopyOption.COPY_ATTRIBUTES)
            Files.copy(source, staged, StandardCopyOption.COPY_ATTRIBUTES)
            val originalHash = sha256(source)
            check(Files.size(backup) == size && Files.size(staged) == size)
            check(sha256(backup) == originalHash && sha256(staged) == originalHash)
            IsolatedWritePreparationResult.Prepared(IsolatedWriteSession(sourceId, source, backup, staged, originalHash, size))
        }.getOrElse {
            runCatching { Files.deleteIfExists(staged) }
            runCatching { Files.deleteIfExists(backup) }
            IsolatedWritePreparationResult.Rejected(IsolatedWritePreparationRejection.COPY_FAILED)
        }
    }

    fun mutate(
        session: IsolatedWriteSession,
        writer: (Path) -> Boolean,
        validator: (Path) -> Boolean,
        cancelled: () -> Boolean = { false },
    ): IsolatedWriteMutationResult {
        if (!sourceUnchanged(session)) return rejected(session, IsolatedWriteMutationRejection.SOURCE_CHANGED_BEFORE_WRITE)
        if (cancelled()) return rejected(session, IsolatedWriteMutationRejection.CANCELLED)
        val wrote = runCatching { writer(session.staged) }.getOrDefault(false)
        if (!wrote) return rejected(session, IsolatedWriteMutationRejection.WRITE_FAILED)
        if (cancelled()) return rejected(session, IsolatedWriteMutationRejection.CANCELLED)
        if (!runCatching { validator(session.staged) }.getOrDefault(false)) return rejected(session, IsolatedWriteMutationRejection.VALIDATION_FAILED)
        if (!sourceUnchanged(session)) return rejected(session, IsolatedWriteMutationRejection.SOURCE_CHANGED_AFTER_WRITE)
        readySessions.add(session.staged)
        return IsolatedWriteMutationResult.ReadyToCommit(session)
    }

    fun commit(
        session: IsolatedWriteSession,
        replace: (source: Path, staged: Path) -> Boolean,
    ): IsolatedWriteCommitResult {
        if (session.staged !in readySessions) return IsolatedWriteCommitResult.Failed(IsolatedWriteCommitRejection.NOT_READY, session.backup)
        if (!sourceUnchanged(session)) return IsolatedWriteCommitResult.Failed(IsolatedWriteCommitRejection.SOURCE_CHANGED, session.backup)
        val replaced = runCatching { replace(session.source, session.staged) }.getOrDefault(false)
        if (!replaced) {
            readySessions.remove(session.staged)
            return IsolatedWriteCommitResult.Failed(IsolatedWriteCommitRejection.REPLACE_FAILED, session.backup)
        }
        val matches = runCatching { Files.size(session.source) == Files.size(session.staged) && sha256(session.source) == sha256(session.staged) }.getOrDefault(false)
        readySessions.remove(session.staged)
        return if (matches) IsolatedWriteCommitResult.Committed
        else IsolatedWriteCommitResult.Failed(IsolatedWriteCommitRejection.POST_COMMIT_MISMATCH, session.backup)
    }

    fun rollback(
        session: IsolatedWriteSession,
        restore: (backup: Path, source: Path) -> Boolean,
    ): IsolatedWriteRollbackResult {
        val restored = runCatching { restore(session.backup, session.source) }.getOrDefault(false)
        val verified = restored && runCatching { sourceUnchanged(session) }.getOrDefault(false)
        return if (verified) IsolatedWriteRollbackResult.RolledBack
        else IsolatedWriteRollbackResult.Failed(session.backup)
    }

    private fun sourceUnchanged(session: IsolatedWriteSession): Boolean =
        Files.size(session.source) == session.originalSize && sha256(session.source) == session.originalSha256

    private fun rejected(session: IsolatedWriteSession, reason: IsolatedWriteMutationRejection): IsolatedWriteMutationResult.Rejected {
        readySessions.remove(session.staged)
        return IsolatedWriteMutationResult.Rejected(reason, session.backup)
    }
}

private fun sha256File(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
