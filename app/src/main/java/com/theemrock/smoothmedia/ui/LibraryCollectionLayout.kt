package com.theemrock.smoothmedia.ui

internal fun albumsGridMinCardWidthDp(): Float = 80f

internal fun albumsGridColumnCount(contentWidthDp: Float): Int =
    ((contentWidthDp + 8f) / (albumsGridMinCardWidthDp() + 8f)).toInt().coerceAtLeast(1)
