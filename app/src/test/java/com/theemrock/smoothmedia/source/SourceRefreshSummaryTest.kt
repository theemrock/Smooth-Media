package com.theemrock.smoothmedia.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceRefreshSummaryTest {
    @Test
    fun failureBeforeAvailable_isNotHidden() {
        assertEquals(
            SourceRefreshResult.StorageFailure,
            selectRefreshSummary(
                listOf(
                    SourceRefreshResult.StorageFailure,
                    SourceRefreshResult.Available("second"),
                ),
            ),
        )
    }

    @Test
    fun storageFailureAfterTemporaryUnavailable_isNotHidden() {
        assertEquals(
            SourceRefreshResult.StorageFailure,
            selectRefreshSummary(
                listOf(
                    SourceRefreshResult.Unavailable("first"),
                    SourceRefreshResult.StorageFailure,
                ),
            ),
        )
    }

    @Test
    fun allAvailable_reportsLatestResultAndEmptyReportsNothing() {
        assertEquals(
            SourceRefreshResult.Available("second"),
            selectRefreshSummary(
                listOf(
                    SourceRefreshResult.Available("first"),
                    SourceRefreshResult.Available("second"),
                ),
            ),
        )
        assertNull(selectRefreshSummary(emptyList()))
    }
}
