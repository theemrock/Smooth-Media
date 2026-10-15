package com.theemrock.smoothmedia.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.MainActivity
import com.theemrock.smoothmedia.library.LibraryLocationState
import com.theemrock.smoothmedia.library.LibraryCollectionType
import com.theemrock.smoothmedia.library.LibraryMetadataState
import com.theemrock.smoothmedia.library.LibrarySong
import com.theemrock.smoothmedia.library.LibraryUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun songsScreen_showsCatalogHeadingAndMissingMetadataState() {
        composeRule.activity.setContent {
            MaterialTheme {
                LibraryScreen(
                    state = LibraryUiState(
                        songs = listOf(
                            LibrarySong(
                                trackId = "track-1",
                                title = "Unknown title",
                                artist = "Unknown artist",
                                album = "Unknown album",
                                durationMs = null,
                                metadataState = LibraryMetadataState.MISSING,
                                locationState = LibraryLocationState.MISSING,
                                locationCount = 1,
                            ),
                        ),
                        totalTracks = 1,
                        loaded = true,
                    ),
                    onNearEnd = {},
                    onSources = {},
                )
            }
        }

        composeRule.onAllNodesWithText("SONGS").assertCountEquals(2)
        composeRule.onNodeWithText("1 TRACKS").assertIsDisplayed()
        composeRule.onNodeWithText("METADATA MISSING").assertIsDisplayed()
    }

    @Test
    fun songsScreen_exposesCollectionNavigation() {
        var selected: LibraryCollectionType? = null
        composeRule.activity.setContent {
            MaterialTheme {
                LibraryScreen(
                    state = LibraryUiState(loaded = true, totalTracks = 1),
                    onNearEnd = {},
                    onSources = {},
                    onCollection = { selected = it },
                )
            }
        }

        composeRule.onNodeWithText("ALBUMS").performClick()

        org.junit.Assert.assertEquals(LibraryCollectionType.ALBUMS, selected)
    }
}
