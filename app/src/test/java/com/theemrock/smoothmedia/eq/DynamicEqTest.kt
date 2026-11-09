package com.theemrock.smoothmedia.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

class DynamicEqTest {
    private val n = EqBands24.COUNT

    /** Levels that already match the target shape, at an arbitrary loudness. */
    private fun onTarget(offset: Double = -30.0) =
        DoubleArray(n) { offset - 1.5 * ln(EqBands24.centersHz[it] / 1000.0) / ln(2.0) }

    @Test
    fun levelsOnTargetNeedNoCorrection() {
        DynamicEq.gainsFor(onTarget(-20.0)).forEach { assertEquals(0.0, it, 1e-9) }
    }

    @Test
    fun loudBandIsCutAndGainStaysWithinLimit() {
        val levels = onTarget().also { it[5] += 30.0 }
        val gains = DynamicEq.gainsFor(levels, strength = 1.0)
        assertTrue(gains[5] <= -DynamicEq.MAX_GAIN_DB + 1e-9)
        gains.forEach { assertTrue(abs(it) <= DynamicEq.MAX_GAIN_DB + 1e-9) }
    }

    @Test
    fun strengthScalesCorrection() {
        val levels = onTarget().also { it[10] += 4.0 }
        val full = DynamicEq.gainsFor(levels, 1.0)[10]
        val half = DynamicEq.gainsFor(levels, 0.5)[10]
        assertEquals(full / 2, half, 1e-9)
    }

    @Test
    fun songPreampCancelsLargestBoost() {
        val quietBand = onTarget().also { it[3] -= 8.0 }
        val windows = listOf(SpectrumWindow(1_000, quietBand), SpectrumWindow(6_000, onTarget()))
        val boost = DynamicEq.gainsFor(quietBand).max()
        assertTrue(boost > 0)
        assertEquals(-boost, DynamicEq.songPreampDb(windows), 1e-9)
    }

    @Test
    fun levelsBlendBetweenWindowsAndSkipSilentOnes() {
        val a = onTarget(-30.0)
        val b = onTarget(-30.0).also { it[3] += 4.0 }
        val windows = listOf(
            SpectrumWindow(1_000, a),
            SpectrumWindow(3_000, null),
            SpectrumWindow(5_000, b),
        )
        val mid = DynamicEq.levelsAt(windows, 3_000)
        assertNotNull(mid)
        assertEquals(a[3] + 2.0, mid!![3], 1e-9)
        assertEquals(a[3], DynamicEq.levelsAt(windows, 0)!![3], 1e-9)
        assertEquals(b[3], DynamicEq.levelsAt(windows, 99_000)!![3], 1e-9)
    }

    @Test
    fun allSilentSongGivesFlatGains() {
        val windows = listOf(SpectrumWindow(1_000, null))
        assertNull(DynamicEq.levelsAt(windows, 500))
        DynamicEq.gainsAt(windows, 500).forEach { assertEquals(0.0, it, 0.0) }
    }

    @Test
    fun slewLimitsChangeSpeed() {
        val out = DynamicEq.slew(DoubleArray(n), DoubleArray(n) { 10.0 }, dtSeconds = 0.1)
        out.forEach { assertEquals(0.2, it, 1e-9) }
        val reached = DynamicEq.slew(DoubleArray(n), DoubleArray(n) { 0.1 }, dtSeconds = 0.1)
        reached.forEach { assertEquals(0.1, it, 1e-9) }
    }

    @Test
    fun profileHas24BandsAndNonPositivePreamp() {
        val profile = DynamicEq.toProfile(DoubleArray(n) { 1.0 }, preampDb = 3.0)
        assertEquals(n, profile.bands.size)
        assertEquals(0.0, profile.preampDb, 0.0)
    }
}
