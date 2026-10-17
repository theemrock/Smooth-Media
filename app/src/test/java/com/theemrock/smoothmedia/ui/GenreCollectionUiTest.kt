package com.theemrock.smoothmedia.ui

import com.theemrock.smoothmedia.library.LibraryGenreArtworkPreview
import org.junit.Assert.assertEquals
import org.junit.Test

class GenreCollectionUiTest {
    @Test
    fun genrePreviewIdsAreDistinctAndLimited() {
        val previews = listOf(
            LibraryGenreArtworkPreview("track-a"),
            LibraryGenreArtworkPreview("track-a"),
            LibraryGenreArtworkPreview("track-b"),
            LibraryGenreArtworkPreview("track-c"),
            LibraryGenreArtworkPreview("track-d"),
            LibraryGenreArtworkPreview("track-e"),
        )

        assertEquals(
            listOf("track-a", "track-b", "track-c", "track-d"),
            genrePreviewIds(previews),
        )
    }
}
