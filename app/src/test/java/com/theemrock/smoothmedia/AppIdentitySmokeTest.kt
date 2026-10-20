package com.theemrock.smoothmedia

import org.junit.Assert.assertTrue
import org.junit.Test

class AppIdentitySmokeTest {
    @Test
    fun generatedApplicationId_matchesSmoothMediaIdentity() {
        assertTrue(
            BuildConfig.APPLICATION_ID == "com.theemrock.smoothmedia" ||
                BuildConfig.APPLICATION_ID == "com.theemrock.smoothmedia.p09test",
        )
    }
}
