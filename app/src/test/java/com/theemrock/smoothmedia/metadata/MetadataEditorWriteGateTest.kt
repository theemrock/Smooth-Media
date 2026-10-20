package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataEditorWriteGateTest {
    @Test
    fun productionGateDefaultsToBlocked() {
        assertEquals(
            MetadataEditorWriteDecision.BLOCKED,
            MetadataEditorWriteGate().decision(),
        )
    }
}
