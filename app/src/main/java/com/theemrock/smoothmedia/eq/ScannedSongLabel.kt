package com.theemrock.smoothmedia.eq

import java.net.URLDecoder

object ScannedSongLabel {
    /** The tag title when present, otherwise the file name (without extension) taken from the content URI. */
    fun title(title: String, locator: String?): String {
        if (title.isNotBlank()) return title
        val decoded = locator?.let { runCatching { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrNull() } ?: return ""
        return decoded.substringAfterLast('/').substringBeforeLast('.').trim()
    }
}
