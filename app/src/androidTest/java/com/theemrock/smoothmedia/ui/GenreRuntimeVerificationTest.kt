package com.theemrock.smoothmedia.ui

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.library.LibraryCollectionRow
import com.theemrock.smoothmedia.library.LibraryCollectionType
import com.theemrock.smoothmedia.library.LibraryCollectionUiState
import com.theemrock.smoothmedia.library.LibraryLocationState
import com.theemrock.smoothmedia.library.LibraryMetadataState
import com.theemrock.smoothmedia.library.LibrarySong
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenreRuntimeVerificationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun genreListAndDetailRenderSyntheticParentChildAndCounts() {
        var selectedGenre: LibraryCollectionRow? = null
        composeRule.setContent {
            MaterialTheme {
                LibraryCollectionScreen(
                    state = LibraryCollectionUiState(
                        type = LibraryCollectionType.GENRES,
                        collections = listOf(
                            LibraryCollectionRow("rock", "Rock", null, 2),
                            LibraryCollectionRow("jazz", "Jazz", null, 1),
                        ),
                        totalCollections = 2,
                        loaded = true,
                        hasMore = false,
                    ),
                    onNearEnd = {},
                    onSongs = {},
                    onCollection = {},
                    onCollectionSelected = { selectedGenre = it },
                    onSources = {},
                )
            }
        }

        composeRule.onNodeWithText("Rock").assertIsDisplayed().performClick()
        assertEquals("rock", selectedGenre?.key)
        composeRule.onNodeWithText("2 şarkı").assertIsDisplayed()

        var selectedChild: LibraryCollectionRow? = null
        composeRule.setContent {
            MaterialTheme {
                LibraryDetailScreen(
                    type = LibraryCollectionType.GENRES,
                    row = LibraryCollectionRow("rock", "Rock", null, 2),
                    songs = listOf(
                        LibrarySong("track-1", "Song One", "Artist", "Album", 1_000L, LibraryMetadataState.COMPLETE, LibraryLocationState.AVAILABLE, 1),
                        LibrarySong("track-2", "Song Two", "Artist", "Album", 2_000L, LibraryMetadataState.MISSING, LibraryLocationState.MISSING, 1),
                    ),
                    genreChildren = listOf(LibraryCollectionRow("fusion", "Fusion", null, 1)),
                    onMenu = {},
                    onSearch = {},
                    onDashboard = {},
                    onAlbumSelected = {},
                    onGenreSelected = { selectedChild = it },
                    onSong = {},
                    onAddToQueue = {},
                    onPlayNext = {},
                )
            }
        }

        composeRule.onNodeWithText("Rock").assertIsDisplayed()
        composeRule.onNodeWithText("Fusion", substring = true).assertIsDisplayed().performClick()
        assertEquals("fusion", selectedChild?.key)
        composeRule.onNodeWithText("Song One").assertIsDisplayed()
        composeRule.onNodeWithText("Song Two").assertIsDisplayed()
    }
}
