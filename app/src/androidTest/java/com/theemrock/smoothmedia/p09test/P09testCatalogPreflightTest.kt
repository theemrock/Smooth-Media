package com.theemrock.smoothmedia.p09test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.theemrock.smoothmedia.source.SourceRegistryFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only Room preflight for the direct am instrument test flow. */
@RunWith(AndroidJUnit4::class)
class P09testCatalogPreflightTest {
    @Test
    fun reportsExpectedActiveCatalogWithoutScanning() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.theemrock.smoothmedia.p09test") {
            "P09TEST preflight yalnız p09test hedefinde çalışır"
        }

        val runtime = SourceRegistryFactory.create(context)
        try {
            val activeCount = runtime.database.trackDao().countActiveTracks()
            System.out.println("P09TEST_PREFLIGHT|catalogActive=$activeCount|expected=$EXPECTED_TRACK_COUNT|PASS")
            assertEquals(EXPECTED_TRACK_COUNT, activeCount)
        } finally {
            runtime.close()
        }
    }

    private companion object {
        const val EXPECTED_TRACK_COUNT = 7_036
    }
}
