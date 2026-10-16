package com.theemrock.smoothmedia.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.MainActivity
import com.theemrock.smoothmedia.library.LibraryLocationState
import com.theemrock.smoothmedia.library.LibraryCollectionType
import com.theemrock.smoothmedia.library.LibraryCollectionRow
import com.theemrock.smoothmedia.library.LibraryCollectionUiState
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
        composeRule.onNodeWithContentDescription("Unavailable: file missing").assertIsDisplayed()
    }

    @Test
    fun songsScreen_exposesCollectionNavigation() {
        var selected: LibraryCollectionType? = null
        var songsSelected = false
        composeRule.activity.setContent {
            MaterialTheme {
                LibraryScreen(
                    state = LibraryUiState(loaded = true, totalTracks = 1),
                    onNearEnd = {},
                    onSources = {},
                    onSongs = { songsSelected = true },
                    onCollection = { selected = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Library section: ALBUMS").performClick()

        org.junit.Assert.assertEquals(LibraryCollectionType.ALBUMS, selected)

        composeRule.onNodeWithContentDescription("Library section: SONGS").performClick()
        org.junit.Assert.assertEquals(true, songsSelected)
    }

    @Test
    fun artistsScreen_showsStatsWithoutVisualPlaceholder_andKeepsNavigation() {
        var selected: String? = null
        composeRule.activity.setContent {
            MaterialTheme {
                LibraryCollectionScreen(
                    state = LibraryCollectionUiState(
                        type = LibraryCollectionType.ARTISTS,
                        collections = listOf(
                            LibraryCollectionRow("artist-1", "Artist", null, 7, 3, 7, 0),
                            LibraryCollectionRow("artist-2", "Artist Two", null, 2, 1, 2, 0),
                        ),
                        totalCollections = 1,
                        loaded = true,
                        hasMore = false,
                    ),
                    onNearEnd = {},
                    onRetry = {},
                    onSongs = {},
                    onCollection = {},
                    onCollectionSelected = { selected = it.key },
                    onSources = {},
                )
            }
        }

        composeRule.onNodeWithText("Artist").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("3 albums · 7 songs").assertIsDisplayed()
        composeRule.onNodeWithText("7 MP3").assertIsDisplayed()
        composeRule.onAllNodesWithText("0 FLAC").assertCountEquals(0)
        val first = composeRule.onNodeWithText("Artist").fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithText("Artist Two").fetchSemanticsNode().boundsInRoot
        assertTrue("Artists must be stacked vertically", second.top > first.bottom)
        org.junit.Assert.assertEquals("artist-1", selected)
    }
}
