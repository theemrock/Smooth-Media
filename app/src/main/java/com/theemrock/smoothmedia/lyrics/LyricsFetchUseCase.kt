package com.theemrock.smoothmedia.lyrics

/** Result of a user-initiated "fetch lyrics" action. */
sealed interface LyricsFetchOutcome {
    data class Stored(val mode: LyricsMode, val source: String) : LyricsFetchOutcome
    data class AlreadyCached(val mode: LyricsMode) : LyricsFetchOutcome
    data object NotFound : LyricsFetchOutcome
    data object Instrumental : LyricsFetchOutcome
    data class Failed(val reason: String) : LyricsFetchOutcome
}

/**
 * Fetches lyrics for one track on explicit user request and stores them in the local cache.
 * Never called automatically: nothing leaves the device unless the user asks for lyrics.
 * Cached lyrics (any provenance) are used first, so the network is only touched once per track.
 */
class LyricsFetchUseCase(
    private val cache: LyricsCache,
    private val client: LrclibClient,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    fun fetch(
        trackId: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
    ): LyricsFetchOutcome {
        cache.get(trackId)?.let { return LyricsFetchOutcome.AlreadyCached(it.mode) }
        // lrclib requires artist_name; without it the request is rejected (HTTP 400), so skip it.
        if (title.isBlank() || artist.isBlank()) return LyricsFetchOutcome.NotFound

        return when (val result = client.lookup(title, artist, album, durationMs)) {
            is LrclibResult.Found -> {
                val synced = result.syncedLyrics
                val plain = result.plainLyrics
                when {
                    !synced.isNullOrBlank() -> store(trackId, LyricsMode.SYNCED, synced)
                    !plain.isNullOrBlank() -> store(trackId, LyricsMode.PLAIN, plain)
                    result.instrumental -> LyricsFetchOutcome.Instrumental
                    else -> LyricsFetchOutcome.NotFound
                }
            }
            LrclibResult.NotFound -> LyricsFetchOutcome.NotFound
            is LrclibResult.Retryable -> LyricsFetchOutcome.Failed(result.reason)
        }
    }

    private fun store(trackId: String, mode: LyricsMode, text: String): LyricsFetchOutcome {
        cache.put(
            CachedLyrics(
                trackId = trackId,
                mode = mode,
                text = text,
                provenance = LyricsProvenance.REMOTE_CACHE,
                source = SOURCE_LRCLIB,
                updatedAtEpochMs = nowEpochMs(),
            ),
        )
        return LyricsFetchOutcome.Stored(mode, SOURCE_LRCLIB)
    }

    companion object {
        const val SOURCE_LRCLIB = "lrclib"
    }
}
