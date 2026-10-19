package com.theemrock.smoothmedia.metadata

internal fun resolveMetadataMissingCount(previous: Int, query: () -> Int): Int =
    runCatching { query() }.getOrElse { previous }
