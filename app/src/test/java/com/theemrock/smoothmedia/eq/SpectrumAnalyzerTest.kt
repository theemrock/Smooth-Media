package com.theemrock.smoothmedia.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class SpectrumAnalyzerTest {
    private val rate = 44_100

    private fun analyze(seconds: Int, sample: (Int) -> Float): List<SpectrumWindow> {
        val analyzer = SpectrumAnalyzer(rate, hopSeconds = 1.0)
        val data = FloatArray(seconds * rate) { sample(it) }
        analyzer.feed(data, data.size)
        return analyzer.finish()
    }

    private fun sine(hz: Double, amp: Double = 0.5) = { n: Int -> (amp * sin(2 * PI * hz * n / rate)).toFloat() }

    private fun nearestBand(hz: Double) = EqBands24.centersHz.indices.minBy { abs(EqBands24.centersHz[it] - hz) }

    @Test
    fun sinePeaksInItsOwnBandAtExpectedLevel() {
        val windows = analyze(12, sine(1_000.0))
        assertTrue("too few windows: ${windows.size}", windows.size >= 11)
        val db = windows[5].bandDb
        assertNotNull(db)
        val peak = db!!.indices.maxBy { db[it] }
        assertEquals(nearestBand(1_000.0), peak)
        // 0.5 amplitude sine has mean square 0.125, about -9 dBFS.
        assertTrue("level was ${db[peak]}", db[peak] in -11.0..-7.0)
    }

    @Test
    fun differentFrequenciesLandInDifferentBands() {
        val low = analyze(4, sine(100.0))[2].bandDb!!
        val high = analyze(4, sine(4_000.0))[2].bandDb!!
        assertTrue(low.indices.maxBy { low[it] } < high.indices.maxBy { high[it] })
    }

    @Test
    fun silenceIsReportedAsNull() {
        val windows = analyze(6) { 0f }
        assertTrue(windows.isNotEmpty())
        windows.forEach { assertNull(it.bandDb) }
    }

    @Test
    fun windowCentersAdvanceByHop() {
        val windows = analyze(8, sine(1_000.0))
        assertTrue(windows[1].centerMs - windows[0].centerMs in 500L..1_500L)
        assertTrue(windows[4].centerMs > windows[3].centerMs)
    }
}
