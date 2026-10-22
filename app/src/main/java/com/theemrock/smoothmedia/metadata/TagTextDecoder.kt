package com.theemrock.smoothmedia.metadata

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Byte-level decoding used by format adapters; no heuristic mojibake replacement. */
object TagTextDecoder {
    data class RepairSuggestion(val original: String, val repaired: String)

    fun decode(bytes: ByteArray, declaredCharset: Charset? = null): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return bytes.copyOfRange(3, bytes.size).toString(StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
        }
        return (declaredCharset ?: StandardCharsets.ISO_8859_1).decode(ByteBuffer.wrap(bytes)).toString()
    }

    /**
     * Repairs only high-confidence text that was decoded as a legacy single-byte
     * charset even though its bytes were UTF-8. The original value remains the
     * raw metadata value; callers can safely apply this repeatedly.
     */
    fun normalizeText(value: String): String {
        if (value.isEmpty() || value.none(SUSPICIOUS_MARKERS::contains)) return value
        val originalScore = score(value)
        return LEGACY_CHARSETS
            .asSequence()
            .mapNotNull { charset -> decodeUtf8RoundTrip(value, charset) }
            .filter { candidate -> candidate != value && score(candidate) > originalScore }
            .maxByOrNull(::score)
            ?: value
    }

    fun suggestRepair(value: String): RepairSuggestion? {
        val repaired = normalizeText(value)
        return repaired.takeIf { it != value }?.let { RepairSuggestion(value, it) }
    }

    private fun decodeUtf8RoundTrip(value: String, legacyCharset: Charset): String? {
        val encoder = legacyCharset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val bytes = runCatching { encoder.encode(CharBuffer.wrap(value)) }.getOrNull()
            ?: return null
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val candidate = runCatching { decoder.decode(bytes.duplicate()).toString() }.getOrNull()
            ?: return null
        val roundTrip = candidate.toByteArray(StandardCharsets.UTF_8).contentEquals(bytes.arrayOrCopy())
        return candidate.takeIf { roundTrip && '\uFFFD' !in it }
    }

    private fun score(value: String): Int {
        val suspicious = value.count(SUSPICIOUS_MARKERS::contains)
        val replacementPenalty = if ('\uFFFD' in value) 100 else 0
        val controlsPenalty = value.count { it.code in 0..8 || it.code in 14..31 }
        return -suspicious * 10 - replacementPenalty - controlsPenalty * 5
    }

    private fun ByteBuffer.arrayOrCopy(): ByteArray =
        if (hasArray() && arrayOffset() == 0 && position() == 0 && limit() == array().size) {
            array()
        } else {
            duplicate().apply { rewind() }.let { copy -> ByteArray(copy.remaining()).also(copy::get) }
        }

    private val SUSPICIOUS_MARKERS = setOf('Ã', 'Â', 'â', 'ð', 'Å', 'Ä', '\uFFFD')
    private val LEGACY_CHARSETS = listOf(
        StandardCharsets.ISO_8859_1,
        Charset.forName("windows-1252"),
        Charset.forName("windows-1254"),
    )

    private fun ByteArray.toString(charset: Charset): String = charset.decode(ByteBuffer.wrap(this)).toString()
}
