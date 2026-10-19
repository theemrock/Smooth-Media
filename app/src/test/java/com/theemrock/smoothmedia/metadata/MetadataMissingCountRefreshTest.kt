package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataMissingCountRefreshTest {
    @Test
    fun refresh_usesActiveTrackMissingCount() {
        val refreshed = resolveMetadataMissingCount(previous = 0) { 8 }

        assertEquals(8, refreshed)
    }

    @Test
    fun refresh_preservesPreviousValueWhenQueryFails() {
        val refreshed = resolveMetadataMissingCount(previous = 8) {
            error("database unavailable")
        }

        assertEquals(8, refreshed)
    }
}
