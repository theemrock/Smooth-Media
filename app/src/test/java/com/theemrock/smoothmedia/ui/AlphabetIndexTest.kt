package com.theemrock.smoothmedia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlphabetIndexTest {
    @Test
    fun bucketsDigitsSymbolsAndLetters() {
        assertEquals("0", AlphabetIndex.bucket("1984"))
        assertEquals("0", AlphabetIndex.bucket("(Anesthesia)"))
        assertEquals("A", AlphabetIndex.bucket("  abba"))
        assertEquals("Ç", AlphabetIndex.bucket("çiçek"))
        assertEquals("Ö", AlphabetIndex.bucket("Ömür"))
        assertEquals("İ", AlphabetIndex.bucket("istanbul"))
        assertEquals("I", AlphabetIndex.bucket("Imagine"))
        assertEquals("E", AlphabetIndex.bucket("Émile"))
        assertEquals("Z", AlphabetIndex.bucket(""))
    }

    @Test
    fun turkishLettersSortWhereTheStripPutsThem() {
        val titles = listOf("Zeki", "Çam", "Cem", "Dolu", "Şan", "Sen", "Ömer", "Oya", "1999", "ışık", "Işık", "İpek")
        val sorted = titles.sortedWith { a, b -> AlphabetIndex.compareTitles(a, b) }
        val ranks = sorted.map { AlphabetIndex.orderOf(AlphabetIndex.bucket(it)) }
        assertEquals(ranks.sorted(), ranks)
        assertEquals("1999", sorted.first())
        assertEquals(listOf("Cem", "Çam"), sorted.filter { it == "Cem" || it == "Çam" })
    }

    @Test
    fun resolveJumpsToTheClosestExistingLetter() {
        val firstIndex = AlphabetIndex.firstIndexByBucket(listOf("0", "0", "A", "A", "C", "Z"))
        assertEquals(2, AlphabetIndex.resolve("A", firstIndex))
        assertEquals(4, AlphabetIndex.resolve("D", firstIndex))
        assertEquals(5, AlphabetIndex.resolve("Y", firstIndex))
        assertEquals(0, AlphabetIndex.resolve("0", firstIndex))
        assertNull(AlphabetIndex.resolve("A", emptyMap()))
    }

    @Test
    fun touchPositionMapsProportionallyToTheStrip() {
        val last = AlphabetIndex.letters.last()
        assertEquals("0", AlphabetIndex.letterAt(0f, 1000f))
        assertEquals(last, AlphabetIndex.letterAt(999f, 1000f))
        assertEquals(last, AlphabetIndex.letterAt(5000f, 1000f))
        assertEquals("0", AlphabetIndex.letterAt(-20f, 1000f))
        assertEquals(AlphabetIndex.letters[AlphabetIndex.letters.size / 2], AlphabetIndex.letterAt(500f, 1000f))
    }
}
