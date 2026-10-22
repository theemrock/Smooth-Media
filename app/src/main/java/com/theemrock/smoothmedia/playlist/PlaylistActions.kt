package com.theemrock.smoothmedia.playlist

/** Keeps the user's selection order while removing repeated or blank IDs. */
fun orderedUniqueTrackIds(trackIds: List<String>): List<String> =
    trackIds.filter(String::isNotBlank).distinct()
