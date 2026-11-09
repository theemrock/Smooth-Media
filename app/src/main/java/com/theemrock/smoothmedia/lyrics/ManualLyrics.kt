package com.theemrock.smoothmedia.lyrics

object ManualLyrics {
    /** Text with at least one [mm:ss.xx] stamp is stored as synced, anything else as plain. Blank input yields null. */
    fun build(trackId: String, input: String, nowEpochMs: Long): CachedLyrics? {
        val text = input.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isEmpty()) return null
        val mode = if (LrcParser.parse(text).lines.isNotEmpty()) LyricsMode.SYNCED else LyricsMode.PLAIN
        return CachedLyrics(trackId, mode, text, LyricsProvenance.USER_OVERRIDE, "manual", nowEpochMs)
    }
}
