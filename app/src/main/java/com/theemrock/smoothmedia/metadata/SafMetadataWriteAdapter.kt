package com.theemrock.smoothmedia.metadata

import java.nio.file.Path

interface PathTagAdapter {
    fun read(path: Path): FileTagSnapshot?

    fun write(path: Path, change: FileTagChange): FileTagWriteResult
}

data class SafMetadataWriteRequest(
    val target: MetadataFileWriteTarget,
    val source: Path,
    val workspace: Path,
    val change: FileTagChange,
    val observedHash: com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash?,
)

data class SafMetadataWriteCapabilities(
    val canReplace: Boolean,
    val canTruncate: Boolean,
    val canSync: Boolean,
    val atomicReplace: Boolean,
) {
    val safeToCommit: Boolean
        get() = canReplace && canTruncate && canSync && atomicReplace
}

sealed interface SafMetadataWriteResult {
    data object Blocked : SafMetadataWriteResult
    data class Prepared(
        val session: IsolatedWriteSession,
        val before: FileTagSnapshot,
        val after: FileTagSnapshot,
    ) : SafMetadataWriteResult
    data object Committed : SafMetadataWriteResult
    data class Rejected(
        val reason: SafMetadataWriteRejection,
        val recoveryBackup: Path? = null,
    ) : SafMetadataWriteResult
}

enum class SafMetadataWriteRejection {
    INVALID_TARGET,
    STALE_HASH,
    STAGING_FAILED,
    READ_FAILED,
    WRITE_FAILED,
    VALIDATION_FAILED,
    PROVIDER_CAPABILITY_UNVERIFIED,
    COMMIT_FAILED,
    ROLLBACK_FAILED,
}

/**
 * Gate-protected staging bridge. It can prepare test-owned mirrors, but it does
 * not invent SAF replace/truncate/fsync guarantees and never commits when the
 * editor gate is blocked.
 */
class SafMetadataWriteAdapter(
    private val writeGate: MetadataEditorWriteGate,
    private val tagAdapter: PathTagAdapter,
) {
    private val coordinators = mutableMapOf<Path, IsolatedMetadataWriteCoordinator>()

    fun stage(
        request: SafMetadataWriteRequest,
        validator: (before: FileTagSnapshot, after: FileTagSnapshot) -> Boolean,
    ): SafMetadataWriteResult {
        if (writeGate.decision() == MetadataEditorWriteDecision.BLOCKED) return SafMetadataWriteResult.Blocked
        if (request.target.locator.isBlank()) return SafMetadataWriteResult.Rejected(SafMetadataWriteRejection.INVALID_TARGET)
        when (MetadataFileWritePolicy.validate(request.target, request.observedHash)) {
            MetadataFileWriteDecision.Allowed -> Unit
            is MetadataFileWriteDecision.Rejected -> return SafMetadataWriteResult.Rejected(SafMetadataWriteRejection.STALE_HASH)
        }
        val before = tagAdapter.read(request.source) ?: return SafMetadataWriteResult.Rejected(SafMetadataWriteRejection.READ_FAILED)
        val coordinator = IsolatedMetadataWriteCoordinator(request.workspace)
        val prepared = (coordinator.prepare(request.source, request.target.sourceId)
            as? IsolatedWritePreparationResult.Prepared)
            ?: return SafMetadataWriteResult.Rejected(SafMetadataWriteRejection.STAGING_FAILED)
        val mutation = coordinator.mutate(
            session = prepared.session,
            writer = { staged -> tagAdapter.write(staged, request.change) is FileTagWriteResult.Written },
            validator = { staged ->
                val after = tagAdapter.read(staged) ?: return@mutate false
                validator(before, after)
            },
        )
        if (mutation !is IsolatedWriteMutationResult.ReadyToCommit) {
            return SafMetadataWriteResult.Rejected(
                if (mutation is IsolatedWriteMutationResult.Rejected && mutation.reason == IsolatedWriteMutationRejection.VALIDATION_FAILED) {
                    SafMetadataWriteRejection.VALIDATION_FAILED
                } else {
                    SafMetadataWriteRejection.WRITE_FAILED
                },
                prepared.session.backup,
            )
        }
        coordinators[prepared.session.staged] = coordinator
        val after = tagAdapter.read(prepared.session.staged) ?: return SafMetadataWriteResult.Rejected(
            SafMetadataWriteRejection.READ_FAILED,
            prepared.session.backup,
        )
        return SafMetadataWriteResult.Prepared(prepared.session, before, after)
    }

    fun commit(
        prepared: SafMetadataWriteResult.Prepared,
        capabilities: SafMetadataWriteCapabilities,
        replace: (source: Path, staged: Path) -> Boolean,
    ): SafMetadataWriteResult {
        if (writeGate.decision() == MetadataEditorWriteDecision.BLOCKED) return SafMetadataWriteResult.Blocked
        if (!capabilities.safeToCommit) {
            return SafMetadataWriteResult.Rejected(
                SafMetadataWriteRejection.PROVIDER_CAPABILITY_UNVERIFIED,
                prepared.session.backup,
            )
        }
        val coordinator = coordinators[prepared.session.staged]
            ?: return SafMetadataWriteResult.Rejected(SafMetadataWriteRejection.COMMIT_FAILED, prepared.session.backup)
        return if (coordinator.commit(prepared.session, replace) is IsolatedWriteCommitResult.Committed) {
            SafMetadataWriteResult.Committed
        } else {
            SafMetadataWriteResult.Rejected(SafMetadataWriteRejection.COMMIT_FAILED, prepared.session.backup)
        }
    }

    fun rollback(
        prepared: SafMetadataWriteResult.Prepared,
        restore: (backup: Path, source: Path) -> Boolean,
    ): SafMetadataWriteResult {
        val coordinator = coordinators[prepared.session.staged]
            ?: return SafMetadataWriteResult.Rejected(SafMetadataWriteRejection.ROLLBACK_FAILED, prepared.session.backup)
        return if (coordinator.rollback(prepared.session, restore) is IsolatedWriteRollbackResult.RolledBack) {
            SafMetadataWriteResult.Prepared(prepared.session, prepared.before, prepared.after)
        } else {
            SafMetadataWriteResult.Rejected(SafMetadataWriteRejection.ROLLBACK_FAILED, prepared.session.backup)
        }
    }
}
