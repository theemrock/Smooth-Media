package com.theemrock.smoothmedia.ui

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.MainActivity
import com.theemrock.smoothmedia.library.LibraryCollectionRow
import com.theemrock.smoothmedia.library.LibraryCollectionType
import com.theemrock.smoothmedia.library.LibraryCollectionUiState
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryCollectionScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun albumsScreen_showsRealCountAndCollectionCard() {
        var selected: LibraryCollectionRow? = null
        composeRule.activity.setContent {
            MaterialTheme {
                LibraryCollectionScreen(
                    state = LibraryCollectionUiState(
                        type = LibraryCollectionType.ALBUMS,
                        collections = listOf(LibraryCollectionRow("album-1", "Album", "Artist", 2)),
                        totalCollections = 1,
                        loaded = true,
                    ),
                    onNearEnd = {},
                    onSongs = {},
                    onCollection = {},
                    onCollectionSelected = { selected = it },
                    onSources = {},
                )
            }
        }

        composeRule.onAllNodesWithText("ALBUMS").assertCountEquals(2)
        composeRule.onNodeWithText("1 collections").assertIsDisplayed()
        composeRule.onNodeWithText("Album").assertIsDisplayed()
        composeRule.onNodeWithText("2 TRACKS").assertIsDisplayed()
        composeRule.onNodeWithText("Album").performClick()
        assertEquals("album-1", selected?.key)
        composeRule.onAllNodesWithText("LOAD MORE").assertCountEquals(0)
    }

    @Test
    fun foldersScreen_reportsUnavailablePaths_withoutInventingFolders() {
        composeRule.activity.setContent {
            MaterialTheme {
                LibraryCollectionScreen(
                    state = LibraryCollectionUiState(
                        type = LibraryCollectionType.FOLDERS,
                        totalCollections = 0,
                        loaded = true,
                    ),
                    onNearEnd = {},
                    onSongs = {},
                    onCollection = {},
                    onSources = {},
                )
            }
        }

        composeRule.onNodeWithText("Folder paths are not available in this catalog").assertIsDisplayed()
    }
}
