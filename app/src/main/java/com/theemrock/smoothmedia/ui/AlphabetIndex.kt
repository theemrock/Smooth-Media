package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * Fixed 0-Z strip shared by every library list. Letters follow the Turkish collation used for
 * sorting (C Ç, G Ğ, I İ, O Ö, S Ş, U Ü) so the strip and the list always agree.
 */
object AlphabetIndex {
    const val DIGITS = "0"
    val letters: List<String> = listOf(DIGITS) + "ABCÇDEFGĞHIİJKLMNOÖPQRSŞTUÜVWXYZ".map { it.toString() }

    private val turkish: Locale = Locale.forLanguageTag("tr-TR")
    private val order: Map<String, Int> = letters.withIndex().associate { it.value to it.index }
    private val turkishOnly = setOf('Ç', 'Ğ', 'İ', 'Ö', 'Ş', 'Ü')

    fun orderOf(letter: String): Int = order[letter] ?: letters.lastIndex

    /** Strip bucket of a title: "0" for digits and symbols, otherwise the first letter. */
    fun bucket(title: String): String {
        val first = title.trim().firstOrNull() ?: return letters.last()
        if (!first.isLetter()) return DIGITS
        val upper = first.toString().uppercase(turkish)[0]
        if (upper in 'A'..'Z' || upper in turkishOnly) return upper.toString()
        val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).firstOrNull()?.uppercaseChar()
        return if (base != null && base in 'A'..'Z') base.toString() else letters.last()
    }

    /** First position of every bucket in the displayed order. */
    fun firstIndexByBucket(buckets: List<String>): Map<String, Int> {
        val result = LinkedHashMap<String, Int>()
        buckets.forEachIndexed { index, bucket -> result.putIfAbsent(bucket, index) }
        return result
    }

    /** Position to scroll to for [letter]; an empty letter jumps to the closest letter that exists. */
    fun resolve(letter: String, firstIndexByBucket: Map<String, Int>): Int? {
        firstIndexByBucket[letter]?.let { return it }
        val target = orderOf(letter)
        return firstIndexByBucket.entries
            .minWithOrNull(compareBy<Map.Entry<String, Int>> { kotlin.math.abs(orderOf(it.key) - target) }.thenBy { it.value })
            ?.value
    }

    /** Strip letter under a touch at [y] of a strip [height] tall; positions map proportionally. */
    fun letterAt(y: Float, height: Float): String {
        if (height <= 0f) return letters.first()
        val index = ((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        return letters[index]
    }

    /** Case and accent insensitive Turkish title order; digits and symbols first, blank titles last. */
    fun compareTitles(left: String, right: String, collator: Collator = newCollator()): Int {
        val l = left.trim()
        val r = right.trim()
        if (l.isEmpty() != r.isEmpty()) return if (l.isEmpty()) 1 else -1
        val lSymbol = !(l.firstOrNull()?.isLetter() ?: false)
        val rSymbol = !(r.firstOrNull()?.isLetter() ?: false)
        if (lSymbol != rSymbol) return if (lSymbol) -1 else 1
        return collator.compare(l, r)
    }

    fun newCollator(): Collator = Collator.getInstance(turkish).apply { strength = Collator.PRIMARY }
}

@Composable
fun AlphabetIndexBar(
    active: String?,
    onLetter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(18.dp)
            .padding(end = 4.dp, top = 8.dp, bottom = 8.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset -> onLetter(AlphabetIndex.letterAt(offset.y, size.height.toFloat())) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onLetter(AlphabetIndex.letterAt(change.position.y, size.height.toFloat()))
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        AlphabetIndex.letters.forEach { letter ->
            Text(
                letter,
                color = if (letter == active) Color.White else Color(0xFF19C3E6),
                fontSize = 9.sp,
                lineHeight = 9.sp,
                maxLines = 1,
            )
        }
    }
}
