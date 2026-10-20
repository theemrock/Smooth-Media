package com.theemrock.smoothmedia.p01x

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicProbePolicyTest {
    @Test
    fun supportedAudio_acceptsOnlyMp3AndFlac() {
        assertTrue(MusicProbePolicy.isSupportedAudio("audio/mpeg", "Track.bin"))
        assertTrue(MusicProbePolicy.isSupportedAudio("audio/flac", "Track.bin"))
        assertTrue(MusicProbePolicy.isSupportedAudio(null, "Track.MP3"))
        assertTrue(MusicProbePolicy.isSupportedAudio("application/octet-stream", "Track.flac"))
        assertFalse(MusicProbePolicy.isSupportedAudio("audio/ogg", "Track.ogg"))
    }

    @Test
    fun musicPath_acceptsMusicRootAndDescendantsOnly() {
        assertTrue(MusicProbePolicy.isMusicPath("Music/"))
        assertTrue(MusicProbePolicy.isMusicPath("Music/Metal/"))
        assertFalse(MusicProbePolicy.isMusicPath("MyMusic/"))
        assertFalse(MusicProbePolicy.isMusicPath(null))
    }

    @Test
    fun genreProbe_preservesPlatformValueWithoutGuessingSeparators() {
        val result = GenreProbe.fromPlatformValue("Heavy Metal;NWOBHM")

        assertEquals(ProbeStatus.INCOMPLETE, result.status)
        assertEquals(listOf("Heavy Metal;NWOBHM"), result.values)
    }

    @Test
    fun genreProbe_reportsMissingValue() {
        val result = GenreProbe.fromPlatformValue(null)

        assertEquals(ProbeStatus.MISSING, result.status)
        assertTrue(result.values.isEmpty())
    }
}
