package com.theemrock.smoothmedia.lyrics

data class LyricsLine(val startMs: Long, val text: String)

data class ParsedLyrics(
    val lines: List<LyricsLine>,
    val metadata: Map<String, String>,
    val warnings: List<String>,
) {
    fun activeLineAt(positionMs: Long): Int {
        val position = positionMs.coerceAtLeast(0L)
        return lines.indexOfLast { it.startMs <= position }
    }

    fun seekTargetForLine(index: Int): Long? = lines.getOrNull(index)?.startMs
}

object LrcParser {
    private val timestamp = Regex("\\[(?:(\\d{1,3}):)?(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,3}))?\\]")
    private val tag = Regex("^\\[([^:]+):([^]]*)]$")

    fun parse(input: String): ParsedLyrics {
        val metadata = linkedMapOf<String, String>()
        val warnings = mutableListOf<String>()
        val parsed = mutableListOf<Pair<Int, LyricsLine>>()
        var offsetMs = 0L
        input.removePrefix("\\uFEFF").lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trimEnd('\r')
            val timestamps = timestamp.findAll(line).toList()
            if (timestamps.isNotEmpty()) {
                val text = line.substringAfterLast(']').trim()
                timestamps.forEach { match ->
                    val start = timestampToMs(match) + offsetMs
                    parsed += index to LyricsLine(start.coerceAtLeast(0L), text)
                }
            } else if (line.isNotBlank()) {
                val tagMatch = tag.matchEntire(line)
                if (tagMatch != null) {
                    val key = tagMatch.groupValues[1].trim().lowercase()
                    val value = tagMatch.groupValues[2].trim()
                    if (key == "offset") {
                        offsetMs = value.toLongOrNull() ?: run { warnings += "Geçersiz offset: $value"; 0L }
                    } else metadata[key] = value
                } else {
                    warnings += "Zaman etiketi olmayan satır: ${index + 1}"
                }
            }
        }
        return ParsedLyrics(
            lines = parsed.sortedWith(compareBy<Pair<Int, LyricsLine>> { it.second.startMs }.thenBy { it.first }).map { it.second },
            metadata = metadata,
            warnings = warnings,
        )
    }

    private fun timestampToMs(match: MatchResult): Long {
        val hours = match.groupValues[1].takeIf(String::isNotEmpty)?.toLongOrNull() ?: 0L
        val minutes = match.groupValues[2].toLong()
        val seconds = match.groupValues[3].toLong()
        val fraction = match.groupValues[4].takeIf(String::isNotEmpty) ?: "0"
        val fractionMs = fraction.padEnd(3, '0').take(3).toLong()
        return (hours * 3_600_000L) + (minutes * 60_000L) + (seconds * 1_000L) + fractionMs
    }
}
