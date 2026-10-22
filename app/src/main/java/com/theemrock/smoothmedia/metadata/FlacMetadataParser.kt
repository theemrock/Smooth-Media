package com.theemrock.smoothmedia.metadata

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

data class FlacBlockSummary(
    val sequence: Int,
    val type: Int,
    val length: Int,
    val rawSha256: String,
)

data class FlacPictureSummary(
    val sequence: Int,
    val rawSha256: String,
    val pictureType: Int,
    val mimeType: String,
    val description: String,
    val width: Long,
    val height: Long,
    val depth: Long,
    val colors: Long,
    val payloadSha256: String,
    val payloadSize: Int,
)

data class FlacInspection(
    val metadataEndOffset: Long,
    val audioSha256: String,
    val blocks: List<FlacBlockSummary>,
    val pictures: List<FlacPictureSummary>,
)

sealed interface FlacParseResult {
    data class Success(val inspection: FlacInspection) : FlacParseResult
    data class Rejected(val reason: FlacParseRejection) : FlacParseResult
}

enum class FlacParseRejection {
    NOT_REGULAR_FILE,
    TOO_LARGE,
    BAD_MAGIC,
    TRUNCATED_HEADER,
    TRUNCATED_BLOCK,
    INVALID_STREAMINFO,
    MISSING_STREAMINFO,
    INVALID_PICTURE,
    INVALID_UTF8,
    IO_ERROR,
}

/** Read-only, TagLib-independent FLAC metadata inspection for staging/test artifacts. */
class FlacMetadataParser(
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val maxBlockBytes: Int = DEFAULT_MAX_BLOCK_BYTES,
) {
    fun parse(path: Path): FlacParseResult {
        if (!Files.isRegularFile(path)) return FlacParseResult.Rejected(FlacParseRejection.NOT_REGULAR_FILE)
        val size = runCatching { Files.size(path) }.getOrNull()
            ?: return FlacParseResult.Rejected(FlacParseRejection.IO_ERROR)
        if (size > maxFileBytes) return FlacParseResult.Rejected(FlacParseRejection.TOO_LARGE)
        return try {
            Files.newInputStream(path).use { raw ->
                DataInputStream(BufferedInputStream(raw)).use { input ->
                    val magic = ByteArray(4)
                    input.readFully(magic)
                    if (!magic.contentEquals(FLAC_MAGIC)) return FlacParseResult.Rejected(FlacParseRejection.BAD_MAGIC)
                    var offset = 4L
                    var sequence = 0
                    var sawStreamInfo = false
                    var last = false
                    val blocks = mutableListOf<FlacBlockSummary>()
                    val pictures = mutableListOf<FlacPictureSummary>()
                    while (!last) {
                        val header = ByteArray(4)
                        try {
                            input.readFully(header)
                        } catch (_: EOFException) {
                            return FlacParseResult.Rejected(FlacParseRejection.TRUNCATED_HEADER)
                        }
                        last = header[0].toInt() and 0x80 != 0
                        val type = header[0].toInt() and 0x7F
                        val length = ((header[1].toInt() and 0xFF) shl 16) or
                            ((header[2].toInt() and 0xFF) shl 8) or
                            (header[3].toInt() and 0xFF)
                        if (length > maxBlockBytes) return FlacParseResult.Rejected(FlacParseRejection.TRUNCATED_BLOCK)
                        val payload = ByteArray(length)
                        try {
                            input.readFully(payload)
                        } catch (_: EOFException) {
                            return FlacParseResult.Rejected(FlacParseRejection.TRUNCATED_BLOCK)
                        }
                        val rawHash = sha256(header + payload)
                        blocks += FlacBlockSummary(sequence, type, length, rawHash)
                        when (type) {
                            STREAMINFO_TYPE -> {
                                if (sequence != 0 || length != STREAMINFO_LENGTH) return FlacParseResult.Rejected(FlacParseRejection.INVALID_STREAMINFO)
                                sawStreamInfo = true
                            }
                            PICTURE_TYPE -> {
                                val picture = parsePicture(sequence, rawHash, payload)
                                    ?: return FlacParseResult.Rejected(FlacParseRejection.INVALID_PICTURE)
                                pictures += picture
                            }
                        }
                        offset += 4L + length
                        sequence++
                    }
                    if (!sawStreamInfo) return FlacParseResult.Rejected(FlacParseRejection.MISSING_STREAMINFO)
                    val audioDigest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        audioDigest.update(buffer, 0, read)
                    }
                    FlacParseResult.Success(
                        FlacInspection(offset, audioDigest.digest().toHex(), blocks, pictures),
                    )
                }
            }
        } catch (_: EOFException) {
            FlacParseResult.Rejected(FlacParseRejection.TRUNCATED_HEADER)
        } catch (_: java.nio.charset.CharacterCodingException) {
            FlacParseResult.Rejected(FlacParseRejection.INVALID_UTF8)
        } catch (_: Exception) {
            FlacParseResult.Rejected(FlacParseRejection.IO_ERROR)
        }
    }

    private fun parsePicture(sequence: Int, rawHash: String, payload: ByteArray): FlacPictureSummary? {
        val cursor = PictureCursor(payload)
        val pictureType = cursor.u32() ?: return null
        val mime = cursor.text() ?: return null
        val description = cursor.text() ?: return null
        val width = cursor.u32() ?: return null
        val height = cursor.u32() ?: return null
        val depth = cursor.u32() ?: return null
        val colors = cursor.u32() ?: return null
        val payloadSize = cursor.u32()?.toInt() ?: return null
        val pictureBytes = cursor.bytes(payloadSize) ?: return null
        if (!cursor.atEnd()) return null
        return FlacPictureSummary(sequence, rawHash, pictureType.toInt(), mime, description, width, height, depth, colors, sha256(pictureBytes), payloadSize)
    }

    private class PictureCursor(private val bytes: ByteArray) {
        private var offset = 0

        fun u32(): Long? {
            if (bytes.size - offset < 4) return null
            val value = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
            offset += 4
            return value
        }

        fun text(): String? {
            val length = u32()?.toInt() ?: return null
            val raw = bytes(length) ?: return null
            return runCatching {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw))
                    .toString()
            }.getOrNull()
        }

        fun bytes(length: Int): ByteArray? {
            if (length < 0 || bytes.size - offset < length) return null
            return bytes.copyOfRange(offset, offset + length).also { offset += length }
        }

        fun atEnd(): Boolean = offset == bytes.size
    }

    private companion object {
        val FLAC_MAGIC = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte())
        const val STREAMINFO_TYPE = 0
        const val STREAMINFO_LENGTH = 34
        const val PICTURE_TYPE = 6
        const val DEFAULT_MAX_FILE_BYTES = 512L * 1024L * 1024L
        const val DEFAULT_MAX_BLOCK_BYTES = 64 * 1024 * 1024
    }
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
