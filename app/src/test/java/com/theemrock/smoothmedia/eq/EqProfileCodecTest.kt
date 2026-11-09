package com.theemrock.smoothmedia.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EqProfileCodecTest {
    @Test
    fun windowsSurviveRoundTripWithSilentOnesKept() {
        val levels = DoubleArray(EqBands24.COUNT) { -40.0 + it * 0.37 }
        val windows = listOf(SpectrumWindow(2_500, levels), SpectrumWindow(7_500, null))

        val back = EqProfileCodec.decode(EqProfileCodec.encode(windows))

        assertNotNull(back)
        assertEquals(2, back!!.size)
        assertEquals(2_500L, back[0].centerMs)
        for (i in levels.indices) assertEquals(levels[i], back[0].bandDb!![i], 0.006)
        assertNull(back[1].bandDb)
    }

    @Test
    fun truncatedBlobDecodesToNull() {
        val bytes = EqProfileCodec.encode(listOf(SpectrumWindow(0, DoubleArray(EqBands24.COUNT))))
        assertNull(EqProfileCodec.decode(bytes.copyOf(bytes.size - 1)))
        assertNull(EqProfileCodec.decode(ByteArray(0)))
    }

    @Test
    fun overrideLevelsRoundTripAndRejectWrongCount() {
        val levels = List(EqBands24.COUNT) { it * 10 - 100 }
        assertEquals(levels, EqProfileCodec.decodeLevels(EqProfileCodec.encodeLevels(levels)))
        assertNull(EqProfileCodec.decodeLevels("1,2,3"))
    }
}
