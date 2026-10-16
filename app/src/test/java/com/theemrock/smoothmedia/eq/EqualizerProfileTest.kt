package com.theemrock.smoothmedia.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerProfileTest {
    @Test
    fun manualLevelIsClampedToSafeRange() {
        assertEquals(1500, EqualizerLevel.clamp(2000))
        assertEquals(-1500, EqualizerLevel.clamp(-2000))
        assertEquals(200, EqualizerLevel.clamp(200))
    }

    @Test
    fun adaptiveStateIsExplicitlyUnavailableWithoutMeasuredInput() {
        val state = AdaptiveEqState.fromMeasuredLevels(emptyMap())

        assertTrue(state.isAvailable.not())
        assertTrue(state.levels.isEmpty())
    }
}
