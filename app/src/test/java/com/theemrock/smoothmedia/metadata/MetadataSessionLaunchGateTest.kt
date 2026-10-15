package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataSessionLaunchGateTest {
    @Test
    fun rejectsDoubleStartUntilTheCurrentSessionReleasesTheGate() {
        val gate = MetadataSessionLaunchGate()

        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())

        gate.release()

        assertTrue(gate.tryAcquire())
    }
}
