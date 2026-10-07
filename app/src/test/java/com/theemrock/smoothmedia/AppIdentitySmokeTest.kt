package com.theemrock.smoothmedia

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIdentitySmokeTest {
    @Test
    fun generatedApplicationId_matchesSmoothMediaIdentity() {
        assertEquals("com.theemrock.smoothmedia", BuildConfig.APPLICATION_ID)
    }
}
