package com.theemrock.smoothmedia.lyrics

enum class LyricsMode { PLAIN, SYNCED }

enum class LyricsProvenance { EMBEDDED, LOCAL_SIDECAR, USER_OVERRIDE, REMOTE_CACHE }

data class CachedLyrics(
    val trackId: String,
    val mode: LyricsMode,
    val text: String,
    val provenance: LyricsProvenance,
    val source: String?,
    val updatedAtEpochMs: Long,
)

interface LyricsCache {
    fun get(trackId: String): CachedLyrics?
    fun put(value: CachedLyrics)
}

class InMemoryLyricsCache : LyricsCache {
    private val values = linkedMapOf<String, CachedLyrics>()

    override fun get(trackId: String): CachedLyrics? = values[trackId]

    override fun put(value: CachedLyrics) {
        values[value.trackId] = value
    }
}
