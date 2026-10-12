package com.theemrock.smoothmedia.ui

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceOrientationPolicyTest {
    @Test
    fun phoneWidth_requestsPortrait() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            DeviceOrientationPolicy.requestedOrientation(599),
        )
    }

    @Test
    fun tabletWidths_leaveOrientationUnspecified() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            DeviceOrientationPolicy.requestedOrientation(600),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            DeviceOrientationPolicy.requestedOrientation(720),
        )
    }
}
