package com.theemrock.smoothmedia.artwork

data class ArtworkCandidate(
    val trackId: String,
    val locationId: String,
    val locator: String,
)

/** Selects embedded artwork without changing catalog state. */
class EmbeddedArtworkSelector(
    private val candidateLoader: (String) -> List<ArtworkCandidate>,
    private val artworkReader: (String) -> ByteArray?,
    private val maxCachedKeys: Int = 24,
) {
    private val cache = object : LinkedHashMap<String, ByteArray?>(maxCachedKeys, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray?>?): Boolean =
            size > maxCachedKeys
    }

    @Synchronized
    fun load(key: String): ByteArray? {
        if (cache.containsKey(key)) return cache[key]
        val artwork = candidateLoader(key).asSequence()
            .mapNotNull { candidate -> runCatching { artworkReader(candidate.locator) }.getOrNull() }
            .firstOrNull { it.isNotEmpty() }
        cache[key] = artwork
        return artwork
    }
}
