package com.theemrock.smoothmedia.metadata

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Reverses the common way Turkish letters appear when UTF-8 text is read through another charset
 * (for example "ı" read as Latin-2 becomes "ą", "ü" read as GBK becomes a CJK ideograph).
 *
 * The table is computed, not hand-written: for every Turkish letter and every candidate charset we
 * encode the letter as UTF-8 and decode those bytes with the wrong charset. Each resulting sequence
 * maps back to the letter. Unknown or ambiguous sequences are left alone, so ordinary text is unchanged.
 */
object TurkishMojibakeTable {
    private const val LETTERS = "ğüşıöçĞÜŞİÖÇ"

    /** Priority order matters when two charsets produce the same sequence; the first one wins. */
    private val CHARSETS = listOf(
        "ISO-8859-2",
        "GBK",
        "Big5",
        "windows-1250",
        "windows-1252",
        "windows-1254",
        "Shift_JIS",
        // Plain Latin-1 turns ş (C5 9F) into "Å" + U+009F, which windows-1252 would show as "ÅŸ"; last so it never wins a tie.
        "ISO-8859-1",
    )

    /** Mojibake sequence → correct letter. Built once. */
    val mappings: Map<String, String> by lazy { build() }

    /** Replaces every known mojibake sequence with its letter. Returns the input when nothing matches. */
    fun repair(value: String): String {
        if (value.isEmpty()) return value
        var out = value
        for (key in mappings.keys.sortedByDescending { it.length }) {
            if (out.contains(key)) out = out.replace(key, mappings.getValue(key))
        }
        return out
    }

    /**
     * Two directions are covered:
     * - UTF-8 bytes read as a legacy charset (ı → "ą" under Latin-2), and
     * - Turkish single-byte bytes (ISO-8859-9 / windows-1254, e.g. Ç = 0xC7) read as a
     *   multi-byte charset (0xC7 under Shift_JIS shows as "ﾇ").
     */
    private val SOURCE_ENCODINGS = listOf("UTF-8", "windows-1254", "ISO-8859-9", "windows-1252")

    private fun build(): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for (source in SOURCE_ENCODINGS) {
            val sourceCharset = runCatching { Charset.forName(source) }.getOrNull() ?: continue
            for (name in CHARSETS) {
                val charset = runCatching { Charset.forName(name) }.getOrNull() ?: continue
                for (letter in LETTERS.toSet()) {
                    val bytes = runCatching { letter.toString().toByteArray(sourceCharset) }.getOrNull() ?: continue
                    val wrong = String(bytes, charset)
                    if (wrong.isEmpty() || wrong == letter.toString() || '�' in wrong) continue
                    map.putIfAbsent(wrong, letter.toString())
                }
            }
        }
        return map
    }
}
