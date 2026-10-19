package com.theemrock.smoothmedia.ui

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.MainActivity
import com.theemrock.smoothmedia.library.LibraryCollectionRow
import com.theemrock.smoothmedia.library.LibraryCollectionType
import com.theemrock.smoothmedia.library.LibraryCollectionUiState
import com.theemrock.smoothmedia.library.LibraryGenreAssignmentUi
import com.theemrock.smoothmedia.library.LibraryLocationState
import com.theemrock.smoothmedia.library.LibraryMetadataState
import com.theemrock.smoothmedia.library.LibrarySong
import com.theemrock.smoothmedia.library.chooseCanonicalGenresOrLegacy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenreRuntimeVerificationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun genreListAndDetailRenderSyntheticParentChildAndCounts() {
        var selectedGenre: LibraryCollectionRow? = null
        composeRule.activity.setContent {
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
        composeRule.activity.setContent {
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

        composeRule.activity.setContent {
            MaterialTheme {
                LibraryDetailScreen(
                    type = LibraryCollectionType.GENRES,
                    row = LibraryCollectionRow("rock", "Rock", null, 1),
                    songs = listOf(
                        LibrarySong("track-1", "Song One", "Artist", "Album", 1_000L, LibraryMetadataState.COMPLETE, LibraryLocationState.AVAILABLE, 1),
                    ),
                    genreAssignments = mapOf(
                        "track-1" to LibraryGenreAssignmentUi("Rock", listOf("Metal", "Fusion")),
                    ),
                    onMenu = {},
                    onSearch = {},
                    onDashboard = {},
                    onAlbumSelected = {},
                    onSong = {},
                    onAddToQueue = {},
                    onPlayNext = {},
                )
            }
        }
        composeRule.onNodeWithText("Primary: Rock").assertIsDisplayed()
        composeRule.onNodeWithText("Secondary: Metal, Fusion").assertIsDisplayed()

        composeRule.activity.setContent {
            MaterialTheme {
                LibraryDetailScreen(
                    type = LibraryCollectionType.GENRES,
                    row = LibraryCollectionRow("rock", "Rock", null, 1),
                    songs = listOf(
                        LibrarySong("track-1", "Song One", "Artist", "Album", 1_000L, LibraryMetadataState.COMPLETE, LibraryLocationState.AVAILABLE, 1),
                    ),
                    genreAssignments = mapOf(
                        "track-1" to LibraryGenreAssignmentUi("Jazz", listOf("Rock")),
                    ),
                    onMenu = {},
                    onSearch = {},
                    onDashboard = {},
                    onAlbumSelected = {},
                    onSong = {},
                    onAddToQueue = {},
                    onPlayNext = {},
                )
            }
        }
        composeRule.onNodeWithText("Primary: Jazz").assertIsDisplayed()
        composeRule.onNodeWithText("Secondary: Rock").assertIsDisplayed()
        composeRule.onNodeWithText("Primary: Rock").assertDoesNotExist()
    }

    @Test
    fun canonicalGenresSuppressLegacyFallbackAndEmptyCanonicalUsesLegacyRows() {
        val legacy = listOf(LibraryCollectionRow("legacy", "Legacy Rock", null, 1))
        val canonical = listOf(LibraryCollectionRow("canonical", "Canonical Rock", null, 2))

        composeRule.activity.setContent {
            MaterialTheme {
                LibraryCollectionScreen(
                    state = LibraryCollectionUiState(
                        type = LibraryCollectionType.GENRES,
                        collections = chooseCanonicalGenresOrLegacy(canonical, legacy),
                        totalCollections = canonical.size,
                        loaded = true,
                        hasMore = false,
                    ),
                    onNearEnd = {},
                    onSongs = {},
                    onCollection = {},
                    onCollectionSelected = {},
                    onSources = {},
                )
            }
        }
        composeRule.onNodeWithText("Canonical Rock").assertIsDisplayed()
        composeRule.onNodeWithText("Legacy Rock").assertDoesNotExist()

        composeRule.activity.setContent {
            MaterialTheme {
                LibraryCollectionScreen(
                    state = LibraryCollectionUiState(
                        type = LibraryCollectionType.GENRES,
                        collections = chooseCanonicalGenresOrLegacy(emptyList(), legacy),
                        totalCollections = legacy.size,
                        loaded = true,
                        hasMore = false,
                    ),
                    onNearEnd = {},
                    onSongs = {},
                    onCollection = {},
                    onCollectionSelected = {},
                    onSources = {},
                )
            }
        }
        composeRule.onNodeWithText("Legacy Rock").assertIsDisplayed()
    }
}
