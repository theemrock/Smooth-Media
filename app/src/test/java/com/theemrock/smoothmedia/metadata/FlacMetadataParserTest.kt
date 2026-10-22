package com.theemrock.smoothmedia.metadata

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlacMetadataParserTest {
    @Test
    fun parsesPicturesUnknownBlocksPaddingAndAudioSeparately() {
        val pictureA = picture(3, "image/jpeg", "front", "cover-a", 640, 480)
        val pictureB = picture(4, "image/png", "back", "cover-b", 320, 240)
        val bytes = fixture(
            blocks = listOf(
                block(0, ByteArray(34)),
                block(4, "ARTIST=Original".toByteArray()),
                block(1, ByteArray(3)),
                block(7, byteArrayOf(9, 8, 7)),
                block(6, pictureA),
                block(6, pictureB, last = true),
            ),
            audio = byteArrayOf(1, 2, 3, 4, 5),
        )
        val path = Files.createTempFile("smooth-flac-", ".flac")
        try {
            Files.write(path, bytes)
            val result = FlacMetadataParser().parse(path) as FlacParseResult.Success
            assertEquals(6, result.inspection.blocks.size)
            assertEquals(listOf(3, 4), result.inspection.pictures.map { it.pictureType })
            assertEquals(listOf("image/jpeg", "image/png"), result.inspection.pictures.map { it.mimeType })
            assertEquals(listOf("front", "back"), result.inspection.pictures.map { it.description })
            assertEquals(listOf(640L, 320L), result.inspection.pictures.map { it.width })
            assertEquals(listOf(7, 7), result.inspection.pictures.map { it.payloadSize })
            assertTrue(result.inspection.blocks.any { it.type == 7 && it.length == 3 })
            assertEquals(sha256(byteArrayOf(1, 2, 3, 4, 5)), result.inspection.audioSha256)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun changedCommentCanBeAcceptedWhilePictureAndAudioArePreserved() {
        val image = picture(3, "image/jpeg", "front", "cover", 100, 100)
        val beforePath = writeFixture("ARTIST=Before", image, byteArrayOf(7, 8))
        val afterPath = writeFixture("ARTIST=After", image, byteArrayOf(7, 8))
        try {
            val parser = FlacMetadataParser()
            val before = (parser.parse(beforePath) as FlacParseResult.Success).inspection
            val after = (parser.parse(afterPath) as FlacParseResult.Success).inspection
            assertEquals(FlacVerificationResult.Preserved, MetadataArtifactVerification.compare(MetadataArtifactVerification.summarize(before), MetadataArtifactVerification.summarize(after)))
            assertTrue(before.blocks[1].rawSha256 != after.blocks[1].rawSha256)
        } finally {
            Files.deleteIfExists(beforePath)
            Files.deleteIfExists(afterPath)
        }
    }

    @Test
    fun comparesIndependentParserOutputOnStagedFile() {
        val image = picture(3, "image/jpeg", "front", "cover", 100, 100)
        val original = writeFixture("ARTIST=Before", image, byteArrayOf(7, 8))
        val workspace = Files.createTempDirectory("smooth-flac-stage-")
        try {
            val preparation = StagedMetadataWritePreparation()
            val prepared = preparation.prepare(original, workspace) as StagedMetadataPreparationResult.Prepared
            Files.write(prepared.artifact.staged, fixture(listOf(block(0, ByteArray(34)), block(4, "ARTIST=After".toByteArray()), block(6, image, last = true)), byteArrayOf(7, 8)))
            val parser = FlacMetadataParser()
            val before = (parser.parse(original) as FlacParseResult.Success).inspection
            val after = (parser.parse(prepared.artifact.staged) as FlacParseResult.Success).inspection
            assertEquals(FlacVerificationResult.Preserved, MetadataArtifactVerification.compare(MetadataArtifactVerification.summarize(before), MetadataArtifactVerification.summarize(after)))
            preparation.abort(prepared.artifact)
        } finally {
            Files.deleteIfExists(original)
            Files.deleteIfExists(workspace)
        }
    }

    @Test
    fun rejectsBadMagicTruncationAndMalformedPicture() {
        val badMagic = Files.createTempFile("smooth-flac-bad-", ".flac")
        val truncated = Files.createTempFile("smooth-flac-truncated-", ".flac")
        val malformed = Files.createTempFile("smooth-flac-picture-", ".flac")
        try {
            Files.write(badMagic, byteArrayOf(1, 2, 3, 4))
            Files.write(truncated, "fLaC".toByteArray() + byteArrayOf(0x80.toByte(), 0, 0))
            Files.write(malformed, fixture(listOf(block(0, ByteArray(34)), block(6, byteArrayOf(0, 0, 0, 3), last = true)), byteArrayOf(1)))
            val parser = FlacMetadataParser()
            assertEquals(FlacParseRejection.BAD_MAGIC, (parser.parse(badMagic) as FlacParseResult.Rejected).reason)
            assertEquals(FlacParseRejection.TRUNCATED_HEADER, (parser.parse(truncated) as FlacParseResult.Rejected).reason)
            assertEquals(FlacParseRejection.INVALID_PICTURE, (parser.parse(malformed) as FlacParseResult.Rejected).reason)
        } finally {
            Files.deleteIfExists(badMagic)
            Files.deleteIfExists(truncated)
            Files.deleteIfExists(malformed)
        }
    }

    private fun writeFixture(comment: String, image: ByteArray, audio: ByteArray) =
        Files.createTempFile("smooth-flac-fixture-", ".flac").also {
            Files.write(it, fixture(listOf(block(0, ByteArray(34)), block(4, comment.toByteArray()), block(6, image, last = true)), audio))
        }

    private fun fixture(blocks: List<ByteArray>, audio: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write("fLaC".toByteArray())
        blocks.forEach(::write)
        write(audio)
    }.toByteArray()

    private fun block(type: Int, payload: ByteArray, last: Boolean = false): ByteArray = ByteArrayOutputStream().apply {
        write(type or if (last) 0x80 else 0)
        write((payload.size shr 16) and 0xFF)
        write((payload.size shr 8) and 0xFF)
        write(payload.size and 0xFF)
        write(payload)
    }.toByteArray()

    private fun picture(type: Int, mime: String, description: String, payload: String, width: Int, height: Int): ByteArray = ByteArrayOutputStream().apply {
        write(u32(type)); write(text(mime)); write(text(description))
        write(u32(width)); write(u32(height)); write(u32(24)); write(u32(0))
        write(u32(payload.toByteArray().size)); write(payload.toByteArray())
    }.toByteArray()

    private fun text(value: String): ByteArray = value.toByteArray().let { u32(it.size) + it }

    private fun u32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte(),
    )

    private fun sha256(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
