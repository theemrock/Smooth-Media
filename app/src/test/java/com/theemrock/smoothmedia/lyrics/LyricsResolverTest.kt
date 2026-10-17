package com.theemrock.smoothmedia.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsResolverTest {
    @Test
    fun sidecarWinsAndUserOverrideIsPreserved() {
        val cache = InMemoryLyricsCache()
        val resolver = LyricsResolver(cache) { 10L }
        val first = resolver.resolve("t1", "embedded", "[00:01.00]Yerel", "song.lrc")!!
        assertEquals(LyricsProvenance.LOCAL_SIDECAR, first.provenance)
        cache.put(CachedLyrics("t1", LyricsMode.PLAIN, "kullanıcı", LyricsProvenance.USER_OVERRIDE, "edit", 20L))
        assertEquals("kullanıcı", resolver.resolve("t1", "embedded", "sidecar")!!.text)
    }

    @Test
    fun syncedFixtureIsCachedWithTrackIdentity() {
        val cache = InMemoryLyricsCache()
        val result = LyricsResolver(cache) { 1L }.resolve("t2", "[00:02.00]İstanbul", null)
        assertEquals(LyricsMode.SYNCED, result!!.mode)
        assertEquals("t2", cache.get("t2")!!.trackId)
    }
}
