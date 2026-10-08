package com.theemrock.smoothmedia.p01x

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P01xSmokeActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<P01xSmokeActivity>()

    @Test
    fun launch_displaysDeveloperOnlyTitleFromResources() {
        val title = composeRule.activity.getString(R.string.p01x_title)

        composeRule.onNodeWithText(title).assertIsDisplayed()
    }
}
