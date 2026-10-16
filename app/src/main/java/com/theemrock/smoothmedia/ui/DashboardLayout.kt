package com.theemrock.smoothmedia.ui

internal fun dashboardCategoryWidthDp(maxWidthDp: Float): Float =
    ((maxWidthDp - 32f - 30f) / 6f).coerceIn(96f, 112f)

internal fun dashboardMediaCardWidthDp(maxWidthDp: Float): Float =
    ((maxWidthDp - 32f - 30f) / 4f).coerceIn(86f, 124f)

internal fun allSongsArtworkSizeDp(maxWidthDp: Float): Float =
    (maxWidthDp * 0.135f).coerceIn(52f, 64f)
