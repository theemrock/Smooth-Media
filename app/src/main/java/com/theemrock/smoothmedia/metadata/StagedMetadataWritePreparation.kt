package com.theemrock.smoothmedia.metadata

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

data class StagedMetadataArtifact(
    val original: Path,
    val staged: Path,
    val originalSha256: String,
)

sealed interface StagedMetadataPreparationResult {
    data class Prepared(val artifact: StagedMetadataArtifact) : StagedMetadataPreparationResult
    data class Rejected(val reason: StagedMetadataPreparationRejection) : StagedMetadataPreparationResult
}

enum class StagedMetadataPreparationRejection {
    ORIGINAL_NOT_REGULAR,
    WORKSPACE_NOT_DIRECTORY,
    SAME_PATH,
    STAGING_ALREADY_EXISTS,
    COPY_FAILED,
}

sealed interface StagedMetadataValidationResult {
    data object Validated : StagedMetadataValidationResult
    data class Rejected(val reason: StagedMetadataValidationRejection) : StagedMetadataValidationResult
}

enum class StagedMetadataValidationRejection {
    STAGING_NOT_REGULAR,
    ORIGINAL_CHANGED,
    VALIDATOR_REJECTED,
    VALIDATOR_FAILED,
}

/**
 * Prepares a private staging copy and validates it without replacing the original.
 * It deliberately has no SAF or production-writer integration: replacement is not
 * safe to claim until the provider's atomicity and rollback contract is verified.
 */
class StagedMetadataWritePreparation(
    private val sha256: (Path) -> String = ::sha256File,
) {
    fun prepare(original: Path, workspace: Path): StagedMetadataPreparationResult {
        if (!Files.isRegularFile(original)) return StagedMetadataPreparationResult.Rejected(StagedMetadataPreparationRejection.ORIGINAL_NOT_REGULAR)
        if (!Files.isDirectory(workspace)) return StagedMetadataPreparationResult.Rejected(StagedMetadataPreparationRejection.WORKSPACE_NOT_DIRECTORY)
        val staged = workspace.resolve("metadata-${UUID.randomUUID()}.stage")
        if (staged.toAbsolutePath().normalize() == original.toAbsolutePath().normalize()) {
            return StagedMetadataPreparationResult.Rejected(StagedMetadataPreparationRejection.SAME_PATH)
        }
        if (Files.exists(staged)) return StagedMetadataPreparationResult.Rejected(StagedMetadataPreparationRejection.STAGING_ALREADY_EXISTS)
        return runCatching {
            Files.copy(original, staged, StandardCopyOption.COPY_ATTRIBUTES)
            StagedMetadataPreparationResult.Prepared(StagedMetadataArtifact(original, staged, sha256(original)))
        }.getOrElse { StagedMetadataPreparationResult.Rejected(StagedMetadataPreparationRejection.COPY_FAILED) }
    }

    fun validate(
        artifact: StagedMetadataArtifact,
        stagedValidator: (Path) -> Boolean,
    ): StagedMetadataValidationResult {
        if (!Files.isRegularFile(artifact.staged)) return StagedMetadataValidationResult.Rejected(StagedMetadataValidationRejection.STAGING_NOT_REGULAR)
        if (runCatching { sha256(artifact.original) }.getOrNull() != artifact.originalSha256) {
            return StagedMetadataValidationResult.Rejected(StagedMetadataValidationRejection.ORIGINAL_CHANGED)
        }
        val valid = runCatching { stagedValidator(artifact.staged) }
            .getOrElse { return StagedMetadataValidationResult.Rejected(StagedMetadataValidationRejection.VALIDATOR_FAILED) }
        return if (valid) StagedMetadataValidationResult.Validated
        else StagedMetadataValidationResult.Rejected(StagedMetadataValidationRejection.VALIDATOR_REJECTED)
    }

    fun abort(artifact: StagedMetadataArtifact): Boolean =
        runCatching { Files.deleteIfExists(artifact.staged) }.getOrDefault(false)
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
