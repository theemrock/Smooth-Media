package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataEditorWriteGateTest {
    @Test
    fun productionCompositionGateIsExplicitlyAllowed() {
        assertEquals(
            MetadataEditorWriteDecision.ALLOWED,
            MetadataEditorWriteGate.production().decision(),
        )
    }

    @Test
    fun productionGateDefaultsToBlocked() {
        assertEquals(
            MetadataEditorWriteDecision.BLOCKED,
            MetadataEditorWriteGate().decision(),
        )
    }
}
