package com.theemrock.smoothmedia.eq

import org.junit.Assert.assertEquals
import org.junit.Test

class ParametricEqTest {

    @Test
    fun peakingBandHitsRequestedGainAtCenterFrequency() {
        val band = PeakBand(frequencyHz = 1000.0, gainDb = 4.0, q = 1.0)
        val biquad = ParametricEq.peaking(band, sampleRateHz = 48_000.0)
        assertEquals(4.0, biquad.magnitudeDb(1000.0, 48_000.0), 0.05)
    }

    @Test
    fun peakingBandIsFlatFarFromCenter() {
        val band = PeakBand(frequencyHz = 1000.0, gainDb = 6.0, q = 2.0)
        val biquad = ParametricEq.peaking(band, sampleRateHz = 48_000.0)
        assertEquals(0.0, biquad.magnitudeDb(20_000.0, 48_000.0), 0.3)
    }

    @Test
    fun manualGainIsClampedToLimit() {
        val band = PeakBand(frequencyHz = 500.0, gainDb = 30.0, q = 1.0)
        val biquad = ParametricEq.peaking(band, sampleRateHz = 48_000.0)
        assertEquals(ParametricEq.MAX_MANUAL_GAIN_DB, biquad.magnitudeDb(500.0, 48_000.0), 0.05)
    }

    @Test
    fun liveCorrectionIsClampedToTwoDb() {
        assertEquals(2.0, ParametricEq.clampLive(3.5), 0.0)
        assertEquals(-2.0, ParametricEq.clampLive(-9.0), 0.0)
        assertEquals(1.2, ParametricEq.clampLive(1.2), 0.0)
    }

    @Test
    fun parsesAutoEqTextFormat() {
        val text = """
            Preamp: -6.2 dB
            Filter 1: ON PK Fc 105 Hz Gain -2.1 dB Q 0.70
            Filter 2: ON PK Fc 2400 Hz Gain 3.4 dB Q 1.40
        """.trimIndent()
        val profile = ParametricEq.parseAutoEq(text)
        assertEquals(-6.2, profile.preampDb, 0.0)
        assertEquals(2, profile.bands.size)
        assertEquals(105.0, profile.bands[0].frequencyHz, 0.0)
        assertEquals(-2.1, profile.bands[0].gainDb, 0.0)
        assertEquals(1.4, profile.bands[1].q, 0.0)
    }
}
