package com.theemrock.smoothmedia.metadata

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Byte-level decoding used by format adapters; heuristic repair only for high-confidence cases. */
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
     * Repairs only high-confidence text that was decoded as a legacy single-byte charset even
     * though its bytes were UTF-8 (for example Turkish "ı" read as Latin-2 becomes "ą"). The
     * candidate is accepted only if it round-trips to valid UTF-8 and has fewer suspicious letters.
     */
    fun normalizeText(value: String): String {
        if (value.isEmpty() || value.none(::isSuspicious)) return value
        val tableRepaired = TurkishMojibakeTable.repair(value)
        if (tableRepaired != value && '�' !in tableRepaired) return tableRepaired
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
        return candidate.takeIf { roundTrip && '�' !in it }
    }

    /** True when the text still contains characters that point to a mis-decoded Turkish/Polish name. */
    fun containsSuspicious(value: String): Boolean = value.any(::isSuspicious)

    /** A Latin-letter marker, or a CJK ideograph (a Turkish/Polish name read through GBK/Big5). */
    private fun isSuspicious(c: Char): Boolean =
        c in SUSPICIOUS_MARKERS || c.code in 0x3400..0x9FFF || c.code in 0xFF61..0xFF9F

    private fun score(value: String): Int {
        val suspicious = value.count(::isSuspicious)
        val replacementPenalty = if ('�' in value) 100 else 0
        val controlsPenalty = value.count { it.code in 0..8 || it.code in 14..31 }
        return -suspicious * 10 - replacementPenalty - controlsPenalty * 5
    }

    private fun ByteBuffer.arrayOrCopy(): ByteArray =
        if (hasArray() && arrayOffset() == 0 && position() == 0 && limit() == array().size) {
            array()
        } else {
            duplicate().apply { rewind() }.let { copy -> ByteArray(copy.remaining()).also(copy::get) }
        }

    /** Letters that show up when UTF-8 Turkish/Polish text is read as a legacy charset. */
    private val SUSPICIOUS_MARKERS = setOf(
        'Ã', 'Â', 'â', 'ð', 'Å', 'Ä', '�',
        'Ă', 'ą', 'ź', 'Ĺ', 'ĺ', 'ž', 'Ľ', 'ľ', 'ę', 'Ę',
    )

    /** Latin-2 first: it is the charset that produced the observed Turkish mojibake (ı → "ą", ü → "Ă" + "ź"). */
    private val LEGACY_CHARSETS = listOf(
        Charset.forName("ISO-8859-2"),
        Charset.forName("GBK"),
        Charset.forName("Big5"),
        Charset.forName("Shift_JIS"),
        Charset.forName("GBK"),
        StandardCharsets.ISO_8859_1,
        Charset.forName("windows-1252"),
        Charset.forName("windows-1254"),
    )

    private fun ByteArray.toString(charset: Charset): String = charset.decode(ByteBuffer.wrap(this)).toString()
}
