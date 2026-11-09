package com.theemrock.smoothmedia.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualEqBandsTest {
    @Test
    fun exposes24BandsAtTheAnalysisCenters() {
        val bands = ManualEqBands.bands(emptyMap())
        assertEquals(24, bands.size)
        assertEquals(32, bands.first().centerFrequencyHz)
        assertEquals(16_000, bands.last().centerFrequencyHz)
    }

    @Test
    fun bypassedManualEqProducesNoBands() {
        assertTrue(ManualEqBands.peakBands(mapOf(0.toShort() to 300), enabled = false).isEmpty())
    }

    @Test
    fun nonZeroLevelBecomesPeakingBandInDb() {
        val bands = ManualEqBands.peakBands(mapOf(0.toShort() to 300, 2.toShort() to 0), enabled = true)
        assertEquals(1, bands.size)
        assertEquals(31.5, bands[0].frequencyHz, 1e-9)
        assertEquals(3.0, bands[0].gainDb, 1e-9)
    }

    @Test
    fun preampCancelsLargestBoostOnly() {
        val bands = ManualEqBands.peakBands(mapOf(3.toShort() to 600, 5.toShort() to -400), enabled = true)
        assertEquals(-6.0, ManualEqBands.preampDb(bands), 1e-9)
        assertEquals(0.0, ManualEqBands.preampDb(ManualEqBands.peakBands(mapOf(1.toShort() to -300), enabled = true)), 0.0)
    }
}
