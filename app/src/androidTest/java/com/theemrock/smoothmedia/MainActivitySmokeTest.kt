package com.theemrock.smoothmedia

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launch_displaysSmoothMediaShell() {
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.app_name)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.phase_00)).assertIsDisplayed()
    }
}
