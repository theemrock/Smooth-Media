package com.theemrock.smoothmedia.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualEqBandsTest {
    @Test
    fun bypassedManualEqProducesNoBands() {
        assertTrue(ManualEqBands.peakBands(mapOf(0.toShort() to 300), enabled = false).isEmpty())
    }

    @Test
    fun nonZeroLevelBecomesPeakingBandInDb() {
        val bands = ManualEqBands.peakBands(mapOf(0.toShort() to 300, 2.toShort() to 0), enabled = true)
        assertEquals(1, bands.size)
        assertEquals(60.0, bands[0].frequencyHz, 0.0)
        assertEquals(3.0, bands[0].gainDb, 1e-9)
    }
}
