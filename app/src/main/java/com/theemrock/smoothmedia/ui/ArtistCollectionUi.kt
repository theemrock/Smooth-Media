package com.theemrock.smoothmedia.ui

import com.theemrock.smoothmedia.library.LibraryGenreArtworkPreview
import com.theemrock.smoothmedia.library.LibraryCollectionRow
import java.text.Collator
import java.util.Locale

fun genrePreviewIds(previews: List<LibraryGenreArtworkPreview>, limit: Int = 4): List<String> =
    previews.asSequence().map { it.trackId }.distinct().take(limit).toList()

data class ArtistLetterGroup(
    val letter: String,
    val artists: List<LibraryCollectionRow>,
)

fun sortArtistRows(
    rows: List<LibraryCollectionRow>,
    ascending: Boolean = true,
): List<LibraryCollectionRow> {
    val collator = Collator.getInstance(Locale.forLanguageTag("tr-TR")).apply {
        strength = Collator.PRIMARY
    }
    val sorted = rows.sortedWith { left, right ->
        val leftTitle = left.title.trim()
        val rightTitle = right.title.trim()
        val leftNumeric = leftTitle.firstOrNull()?.isDigit() == true || leftTitle.firstOrNull()?.isLetter() == false
        val rightNumeric = rightTitle.firstOrNull()?.isDigit() == true || rightTitle.firstOrNull()?.isLetter() == false
        when {
            leftNumeric != rightNumeric -> if (leftNumeric) -1 else 1
            else -> collator.compare(leftTitle, rightTitle).takeIf { it != 0 } ?: left.key.compareTo(right.key)
        }
    }
    return if (ascending) sorted else sorted.asReversed()
}

fun groupArtistRows(rows: List<LibraryCollectionRow>): List<ArtistLetterGroup> =
    sortArtistRows(rows).groupBy { row ->
        row.title.trim().firstOrNull()?.uppercase() ?: "#"
    }.map { (letter, artists) -> ArtistLetterGroup(letter, artists) }
