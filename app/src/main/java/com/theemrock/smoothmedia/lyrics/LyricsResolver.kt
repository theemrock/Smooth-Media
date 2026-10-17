package com.theemrock.smoothmedia.lyrics

/**
 * Deterministic local resolver. Readers are supplied by the SAF/metadata adapters;
 * this class never performs network access and never guesses a track match.
 */
class LyricsResolver(
    private val cache: LyricsCache,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    fun resolve(
        trackId: String,
        embedded: String?,
        sidecar: String?,
        sidecarSource: String? = null,
    ): CachedLyrics? {
        val cached = cache.get(trackId)
        if (cached != null && cached.provenance == LyricsProvenance.USER_OVERRIDE) return cached
        val candidate = sidecar?.takeIf(String::isNotBlank)?.let {
            CachedLyrics(trackId, modeOf(it), it, LyricsProvenance.LOCAL_SIDECAR, sidecarSource, nowEpochMs())
        } ?: embedded?.takeIf(String::isNotBlank)?.let {
            CachedLyrics(trackId, modeOf(it), it, LyricsProvenance.EMBEDDED, "embedded", nowEpochMs())
        }
        if (candidate != null) cache.put(candidate)
        return candidate ?: cached
    }

    private fun modeOf(text: String): LyricsMode =
        if (LrcParser.parse(text).lines.isNotEmpty()) LyricsMode.SYNCED else LyricsMode.PLAIN
}
