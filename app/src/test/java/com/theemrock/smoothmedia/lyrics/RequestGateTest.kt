package com.theemrock.smoothmedia.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class RequestGateTest {

    @Test
    fun `calls are spaced at least the minimum interval apart`() {
        var clock = 0L
        val starts = mutableListOf<Long>()
        val gate = RequestGate(
            minIntervalMs = 300L,
            nowMs = { clock },
            sleepMs = { ms -> clock += ms },
        )

        repeat(4) {
            gate.await()
            starts += clock
        }

        assertEquals(listOf(0L, 300L, 600L, 900L), starts)
    }

    @Test
    fun `a caller arriving after the interval does not wait`() {
        var clock = 0L
        val sleeps = mutableListOf<Long>()
        val gate = RequestGate(
            minIntervalMs = 300L,
            nowMs = { clock },
            sleepMs = { ms -> sleeps += ms; clock += ms },
        )

        gate.await()
        clock += 1_000L
        gate.await()

        assertEquals(emptyList<Long>(), sleeps)
    }
}
