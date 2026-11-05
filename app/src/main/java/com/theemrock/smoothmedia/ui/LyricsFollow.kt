package com.theemrock.smoothmedia.ui

import com.theemrock.smoothmedia.playback.RepeatMode

/** Fraction of the width, on each side, where a horizontal swipe does not switch lyrics mode. */
const val LyricsSwipeEdgeFraction = 0.15f

/** True when [x] lies in the middle 70% of [width]; edge strips are reserved for system gestures. */
fun isInLyricsSwipeZone(x: Float, width: Int): Boolean =
    width > 0 && x >= width * LyricsSwipeEdgeFraction && x <= width * (1f - LyricsSwipeEdgeFraction)

/** Row height to centre: the measured one, else the mean of visible rows, else [fallback]. */
fun estimateLyricsItemHeight(measured: Int?, visibleHeights: List<Int>, fallback: Int = 0): Int =
    measured?.takeIf { it > 0 } ?: visibleHeights.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.toInt() ?: fallback

/** `scrollOffset` for animateScrollToItem that puts an item of [itemHeight] in the viewport centre. */
fun lyricsCenterScrollOffset(viewportHeight: Int, itemHeight: Int): Int =
    -((viewportHeight - itemHeight) / 2)

/** Badge drawn on the repeat icon: "1" for ONE, "A" for ALL, none when off. */
fun repeatBadge(mode: RepeatMode): String? = when (mode) {
    RepeatMode.OFF -> null
    RepeatMode.ONE -> "1"
    RepeatMode.ALL -> "A"
}

/** Whether a shuffle or repeat control should be drawn as active. */
fun isRepeatActive(mode: RepeatMode): Boolean = mode != RepeatMode.OFF

/** Pull-down dismiss is ignored in the top 10% (notification shade area). */
fun isNowPlayingDismissStart(y: Float, height: Int): Boolean = height > 0 && y >= height * 0.10f
