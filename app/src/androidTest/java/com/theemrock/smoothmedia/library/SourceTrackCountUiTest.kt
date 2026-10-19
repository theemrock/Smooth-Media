package com.theemrock.smoothmedia.library

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.MainActivity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.ui.SourceManagementScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceTrackCountUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun sourceCard_displaysSourceBasedCount_withoutUsingUniqueGlobalCount() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    SourceManagementScreen(
                        sources = listOf(source("source-a")),
                        catalogTrackCounts = mapOf("source-a" to 2),
                        statusMessage = null,
                        onAdd = {},
                        onRefresh = {},
                        onRemove = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Katalog: 2 şarkı").assertIsDisplayed()
    }

    private fun source(id: String) = LibrarySourceEntity(
        sourceId = id,
        sourceType = LibrarySourceType.SAF_TREE,
        rootUri = "content://tree/$id",
        volumeName = null,
        persistedPermissionFlags = 1,
        permissionState = SourcePermissionState.GRANTED,
        availability = SourceAvailability.AVAILABLE,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
        selectionState = SourceSelectionState.ACTIVE,
    )
}
