package com.theemrock.smoothmedia.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsCacheTest {
    @Test
    fun `cache is keyed by track identity and preserves provenance and mode`() {
        val cache = InMemoryLyricsCache()
        val synced = CachedLyrics(
            trackId = "track-1",
            mode = LyricsMode.SYNCED,
            text = "[00:01.00]Merhaba",
            provenance = LyricsProvenance.LOCAL_SIDECAR,
            source = "album/song.lrc",
            updatedAtEpochMs = 10L,
        )
        cache.put(synced)

        assertEquals(synced, cache.get("track-1"))
        assertNull(cache.get("track-2"))
        assertEquals(LyricsProvenance.LOCAL_SIDECAR, cache.get("track-1")?.provenance)
    }
}
