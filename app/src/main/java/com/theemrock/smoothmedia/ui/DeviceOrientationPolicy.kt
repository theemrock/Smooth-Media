package com.theemrock.smoothmedia.ui

import android.content.pm.ActivityInfo

object DeviceOrientationPolicy {
    private const val TABLET_MIN_SMALLEST_WIDTH_DP = 600

    fun requestedOrientation(smallestScreenWidthDp: Int): Int =
        if (smallestScreenWidthDp < TABLET_MIN_SMALLEST_WIDTH_DP) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
}
