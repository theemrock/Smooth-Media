package com.theemrock.smoothmedia.ui

import com.theemrock.smoothmedia.library.LibraryCollectionRow
import org.junit.Test
import org.junit.Assert.assertEquals

class ArtistCollectionUiTest {
    @Test
    fun groupsArtistsByAlphabeticalInitialAndKeepsRowsSorted() {
        val groups = groupArtistRows(
            listOf(
                artist("B.B. King"),
                artist("AC/DC"),
                artist("Aerosmith"),
                artist("#Unknown"),
            ),
        )

        assertEquals(listOf("#", "A", "B"), groups.map { it.letter })
        assertEquals(listOf("AC/DC", "Aerosmith"), groups[1].artists.map { it.title })
    }

    @Test
    fun descendingSortReversesArtistOrderWithoutChangingRows() {
        val rows = listOf(artist("AC/DC"), artist("B.B. King"), artist("Aerosmith"))

        assertEquals(
            listOf("B.B. King", "Aerosmith", "AC/DC"),
            sortArtistRows(rows, ascending = false).map { it.title },
        )
    }

    @Test
    fun sortsTurkishLettersWithLocaleAwareOrder() {
        val rows = listOf("Ümit", "Şebnem", "Özlem", "İbrahim", "Ğökhan", "Çelik")
            .map(::artist)

        assertEquals(
            listOf("Çelik", "Ğökhan", "İbrahim", "Özlem", "Şebnem", "Ümit"),
            sortArtistRows(rows).map { it.title },
        )
    }

    private fun artist(title: String) = LibraryCollectionRow(
        key = title,
        title = title,
        subtitle = null,
        trackCount = 1,
        albumCount = 1,
    )
}
