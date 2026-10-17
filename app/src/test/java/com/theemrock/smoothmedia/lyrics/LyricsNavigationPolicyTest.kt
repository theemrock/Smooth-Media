package com.theemrock.smoothmedia.lyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsNavigationPolicyTest {
    @Test fun cachedResultAppliesOnlyToTheRequestedTrack() {
        assertTrue(LyricsNavigationPolicy.canApply("track-1", "track-1"))
        assertFalse(LyricsNavigationPolicy.canApply("track-1", "track-2"))
        assertFalse(LyricsNavigationPolicy.canApply("track-1", null))
    }
}
