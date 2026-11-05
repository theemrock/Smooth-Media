package com.theemrock.smoothmedia.lyrics

/** Result of a lyrics lookup for one track. */
sealed interface LyricsFetchOutcome {
    data class Stored(val mode: LyricsMode, val source: String) : LyricsFetchOutcome
    data class AlreadyCached(val mode: LyricsMode) : LyricsFetchOutcome
    data object NotFound : LyricsFetchOutcome
    data object Instrumental : LyricsFetchOutcome
    data class Failed(val reason: String) : LyricsFetchOutcome
}

/** One-track lyrics lookup, as used by the bulk fetcher. */
fun interface LyricsTrackFetch {
    fun fetch(trackId: String, title: String, artist: String, album: String?, durationMs: Long?): LyricsFetchOutcome
}

/**
 * Looks up lyrics for one track and stores them in the local cache.
 *
 * Lookups that end without words are recorded by kind (NOT_FOUND, INSTRUMENTAL, ERROR) so that
 * bulk fetch does not repeat them by itself; a successful store clears the record.
 * Cached lyrics are used first, so the network is touched at most once per track per run.
 */
class LyricsFetchUseCase(
    private val cache: LyricsCache,
    private val client: LrclibClient,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val recordMiss: (trackId: String, checkedAtEpochMs: Long, kind: String) -> Unit = { _, _, _ -> },
    private val clearMiss: (trackId: String) -> Unit = {},
) : LyricsTrackFetch {
    override fun fetch(
        trackId: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
    ): LyricsFetchOutcome {
        cache.get(trackId)?.let { return LyricsFetchOutcome.AlreadyCached(it.mode) }
        // lrclib requires artist_name; without it the request is rejected (HTTP 400), so skip it.
        if (title.isBlank() || artist.isBlank()) return notFound(trackId)

        val first = client.lookup(title, artist, album, durationMs)
        val result = withBaseTitleFallback(first, title, artist, album, durationMs)

        return when (result) {
            is LrclibResult.Found -> {
                val synced = result.syncedLyrics
                val plain = result.plainLyrics
                when {
                    !synced.isNullOrBlank() -> store(trackId, LyricsMode.SYNCED, synced)
                    !plain.isNullOrBlank() -> store(trackId, LyricsMode.PLAIN, plain)
                    result.instrumental -> instrumental(trackId)
                    else -> notFound(trackId)
                }
            }
            LrclibResult.NotFound -> notFound(trackId)
            is LrclibResult.Retryable -> {
                recordMiss(trackId, nowEpochMs(), KIND_ERROR)
                LyricsFetchOutcome.Failed(result.reason)
            }
        }
    }

    /**
     * A version suffix such as "(Alter Ego Remix)" can make lrclib return only a sungless version.
     * When the first lookup gives no words, the same title without the suffix is tried once; its
     * lyrics are used only if they exist. Transient failures are passed through unchanged.
     */
    private fun withBaseTitleFallback(
        first: LrclibResult,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
    ): LrclibResult {
        val hasWords = first is LrclibResult.Found && (!first.syncedLyrics.isNullOrBlank() || !first.plainLyrics.isNullOrBlank())
        if (first is LrclibResult.Retryable || hasWords) return first
        val base = baseTitle(title) ?: return first
        val second = client.lookup(base, artist, album, durationMs)
        return if (second is LrclibResult.Found && (!second.syncedLyrics.isNullOrBlank() || !second.plainLyrics.isNullOrBlank())) {
            second
        } else {
            first
        }
    }

    private fun notFound(trackId: String): LyricsFetchOutcome {
        recordMiss(trackId, nowEpochMs(), KIND_NOT_FOUND)
        return LyricsFetchOutcome.NotFound
    }

    private fun instrumental(trackId: String): LyricsFetchOutcome {
        recordMiss(trackId, nowEpochMs(), KIND_INSTRUMENTAL)
        return LyricsFetchOutcome.Instrumental
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
        clearMiss(trackId)
        return LyricsFetchOutcome.Stored(mode, SOURCE_LRCLIB)
    }

    companion object {
        /** Title without parenthesised or bracketed suffixes, or null when nothing would change. */
        internal fun baseTitle(title: String): String? {
            val stripped = title.replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "").trim()
            return stripped.takeIf { it.isNotBlank() && it != title }
        }

        const val SOURCE_LRCLIB = "lrclib"
        const val KIND_NOT_FOUND = "NOT_FOUND"
        const val KIND_INSTRUMENTAL = "INSTRUMENTAL"
        const val KIND_ERROR = "ERROR"
    }
}
