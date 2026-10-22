package com.theemrock.smoothmedia.metadata

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StagedMetadataWritePreparationTest {
    @Test
    fun preparationCopiesOnlyToWorkspaceAndLeavesOriginalUntouched() {
        withFixture { original, workspace ->
            val preparation = StagedMetadataWritePreparation()
            val result = preparation.prepare(original, workspace) as StagedMetadataPreparationResult.Prepared
            assertEquals("original-audio", read(original))
            assertEquals("original-audio", read(result.artifact.staged))
            assertTrue(preparation.validate(result.artifact) { read(it) == "original-audio" } is StagedMetadataValidationResult.Validated)
            preparation.abort(result.artifact)
            assertFalse(Files.exists(result.artifact.staged))
        }
    }

    @Test
    fun simulatedNativeFailureIsRolledBackByDeletingStagingOnly() {
        withFixture { original, workspace ->
            val preparation = StagedMetadataWritePreparation()
            val prepared = preparation.prepare(original, workspace) as StagedMetadataPreparationResult.Prepared
            write(prepared.artifact.staged, "partially-written")
            assertTrue(preparation.abort(prepared.artifact))
            assertEquals("original-audio", read(original))
            assertFalse(Files.exists(prepared.artifact.staged))
        }
    }

    @Test
    fun originalChangeAndValidationMismatchAreRejected() {
        withFixture { original, workspace ->
            val preparation = StagedMetadataWritePreparation()
            val prepared = preparation.prepare(original, workspace) as StagedMetadataPreparationResult.Prepared
            write(prepared.artifact.staged, "new-tags")
            assertEquals(
                StagedMetadataValidationResult.Rejected(StagedMetadataValidationRejection.VALIDATOR_REJECTED),
                preparation.validate(prepared.artifact) { false },
            )
            write(original, "changed-original")
            assertEquals(
                StagedMetadataValidationResult.Rejected(StagedMetadataValidationRejection.ORIGINAL_CHANGED),
                preparation.validate(prepared.artifact) { true },
            )
            preparation.abort(prepared.artifact)
        }
    }

    @Test
    fun cancellationCanAbortWithoutReplacingOriginal() {
        withFixture { original, workspace ->
            val preparation = StagedMetadataWritePreparation()
            val prepared = preparation.prepare(original, workspace) as StagedMetadataPreparationResult.Prepared
            assertTrue(preparation.abort(prepared.artifact))
            assertEquals("original-audio", read(original))
        }
    }

    private fun withFixture(block: (java.nio.file.Path, java.nio.file.Path) -> Unit) {
        val root = Files.createTempDirectory("smooth-id3-stage-")
        val original = root.resolve("song.flac")
        val workspace = root.resolve("stage")
        Files.createDirectories(workspace)
        write(original, "original-audio")
        try {
            block(original, workspace)
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun read(path: java.nio.file.Path): String = String(Files.readAllBytes(path), Charsets.UTF_8)

    private fun write(path: java.nio.file.Path, value: String) {
        Files.write(path, value.toByteArray(Charsets.UTF_8))
    }
}
