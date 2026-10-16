package com.theemrock.smoothmedia.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueueCodecTest {
    @Test
    fun `codec preserves queue field delimiters and unicode`() {
        val value = "Björk:Live|夜"
        assertEquals(value, PlaybackQueueCodec.decode(PlaybackQueueCodec.encode(value)))
    }
}
