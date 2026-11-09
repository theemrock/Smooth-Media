package com.theemrock.smoothmedia.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class EqScannerTest {
    private class FakeStore(var pending: List<String>) : EqProfileStore {
        val saved = linkedMapOf<String, ByteArray>()
        override fun pendingTrackIds(analyzerVersion: Int) = pending.filter { it !in saved }
        override fun save(trackId: String, analyzerVersion: Int, signature: String, windows: ByteArray) {
            saved[trackId] = windows
        }
    }

    private val sineDecoder = PcmDecoder { _, sink ->
        val rate = 44_100
        val data = FloatArray(rate * 6) { (0.5 * sin(2 * PI * 1_000.0 * it / rate)).toFloat() }
        sink(data, data.size, rate)
    }

    @Test
    fun scansTrackIntoDecodableWindows() {
        val store = FakeStore(listOf("a"))
        val scanner = EqScanner(sineDecoder, store, uriFor = { "content://$it" })

        assertTrue(scanner.scanTrack("a"))

        val windows = EqProfileCodec.decode(store.saved.getValue("a"))
        assertNotNull(windows)
        assertTrue(windows!!.isNotEmpty())
        assertNotNull(windows.first().bandDb)
    }

    @Test
    fun failedTrackStoresEmptyProfileSoItIsNotRetried() {
        val store = FakeStore(listOf("bad", "missing"))
        val broken = PcmDecoder { _, _ -> error("codec exploded") }
        val scanner = EqScanner(broken, store, uriFor = { if (it == "missing") null else "content://$it" })

        assertFalse(scanner.scanTrack("bad"))
        assertFalse(scanner.scanTrack("missing"))

        assertEquals(0, EqProfileCodec.decode(store.saved.getValue("bad"))!!.size)
        assertTrue(store.pendingTrackIds(EqProfileCodec.ANALYZER_VERSION).isEmpty())
    }

    @Test
    fun priorityTracksScanFirstAndStopFlagIsHonoured() {
        val store = FakeStore(listOf("a", "b", "c"))
        val scanner = EqScanner(sineDecoder, store, uriFor = { "content://$it" })
        var calls = 0

        scanner.scanPending(priority = listOf("c")) { calls++ >= 2 }

        assertEquals(listOf("c", "a"), store.saved.keys.toList())
    }
}
