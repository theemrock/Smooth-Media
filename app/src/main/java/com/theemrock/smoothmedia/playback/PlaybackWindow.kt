package com.theemrock.smoothmedia.playback

data class PlaybackWindow(
    val items: List<QueueItem>,
    val selectedIndex: Int,
) {
    companion object {
        private const val PREVIOUS_COUNT = 1
        private const val FOLLOWING_COUNT = 2

        fun select(items: List<QueueItem>, selectedEntryId: String): PlaybackWindow? {
            val selectedIndex = items.indexOfFirst { it.entryId == selectedEntryId }
            if (selectedIndex < 0) return null
            val start = (selectedIndex - PREVIOUS_COUNT).coerceAtLeast(0)
            val endExclusive = (selectedIndex + FOLLOWING_COUNT + 1).coerceAtMost(items.size)
            return PlaybackWindow(
                items = items.subList(start, endExclusive),
                selectedIndex = selectedIndex - start,
            )
        }

        fun needsRefresh(
            activeWindowEntryIds: List<String>,
            currentEntryId: String,
            hasFollowingDomainItem: Boolean,
        ): Boolean {
            if (!hasFollowingDomainItem) return false
            val currentIndex = activeWindowEntryIds.indexOf(currentEntryId)
            return currentIndex >= (activeWindowEntryIds.lastIndex - 1).coerceAtLeast(0)
        }
    }
}

object PlaybackWindowResolver {
    fun resolve(
        items: List<QueueItem>,
        selectedEntryId: String,
        resolveLocation: (QueueItem) -> PlaybackLocation?,
    ): PlaybackWindowResolution? {
        val window = PlaybackWindow.select(items, selectedEntryId) ?: return null
        val locations = window.items.mapNotNull { item ->
            resolveLocation(item)?.let { item.entryId to it }
        }.toMap()
        return PlaybackWindowResolution(window.items, locations, window.selectedIndex)
    }
}

data class PlaybackWindowResolution(
    val items: List<QueueItem>,
    val locations: Map<String, PlaybackLocation>,
    val startIndex: Int,
)
