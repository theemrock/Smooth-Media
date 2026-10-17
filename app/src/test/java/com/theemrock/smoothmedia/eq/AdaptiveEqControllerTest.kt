package com.theemrock.smoothmedia.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveEqControllerTest {
    @Test
    fun `controller clamps measured levels and manual mode wins`() {
        val controller = AdaptiveEqController()
        assertTrue(controller.updateMeasured(mapOf(0.toShort() to 2000)))
        assertEquals(EqualizerLevel.MAX_MILLIBEL, controller.levels[0.toShort()])
        controller.setMode(EqualizerMode.MANUAL)
        assertFalse(controller.updateMeasured(mapOf(0.toShort() to -1000)))
        controller.setManual(0.toShort(), -1000)
        assertEquals(-1000, controller.levels[0.toShort()])
    }
}
