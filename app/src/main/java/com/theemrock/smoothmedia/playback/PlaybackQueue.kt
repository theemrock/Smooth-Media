package com.theemrock.smoothmedia.playback

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import android.content.Context
import java.util.Base64
import java.util.UUID
import kotlin.random.Random

enum class QueueMode { GHOST, USER }

enum class QueueEntryOrigin { GHOST, MANUAL }

data class GhostPlaybackContext(
    val sourceKey: String? = null,
    val trackIds: List<String> = emptyList(),
)

data class QueueItem(
    val trackId: String,
    val entryId: String = UUID.randomUUID().toString(),
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long? = null,
    val origin: QueueEntryOrigin = QueueEntryOrigin.MANUAL,
)

enum class RepeatMode { OFF, ALL, ONE }

data class PlaybackQueueState(
    val items: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val shuffle: Boolean = false,
    val shuffleOrder: List<String> = emptyList(),
    val repeat: RepeatMode = RepeatMode.OFF,
    val positionMs: Long = 0L,
    val autoPlayOnRestore: Boolean = false,
    val mode: QueueMode = QueueMode.USER,
    val ghostContext: GhostPlaybackContext? = null,
) {
    val current: QueueItem? get() = items.getOrNull(currentIndex)
    val upcoming: List<QueueItem> get() = if (currentIndex < 0) items else items.drop(currentIndex + 1)
}

/** Single domain queue policy; Media3 receives an ordered projection of this state. */
class PlaybackQueueController {
    val state: MutableState<PlaybackQueueState> = mutableStateOf(PlaybackQueueState())

    fun replace(items: List<QueueItem>) {
        val unique = items.distinctBy { it.trackId }
        state.value = state.value.copy(
            items = unique,
            currentIndex = if (unique.isEmpty()) -1 else 0,
            positionMs = 0L,
            mode = QueueMode.USER,
            ghostContext = null,
            shuffleOrder = emptyList(),
        )
    }

    fun startGhost(items: List<QueueItem>, selectedIndex: Int, sourceKey: String? = null) {
        val unique = items.map { it.copy(origin = QueueEntryOrigin.GHOST) }.distinctBy { it.trackId }
        if (unique.isEmpty()) {
            state.value = state.value.copy(items = emptyList(), currentIndex = -1, mode = QueueMode.GHOST, ghostContext = GhostPlaybackContext(sourceKey))
            return
        }
        val selectedTrackId = items.getOrNull(selectedIndex)?.trackId
        val actualIndex = unique.indexOfFirst { it.trackId == selectedTrackId }.takeIf { it >= 0 } ?: selectedIndex.coerceIn(unique.indices)
        state.value = state.value.copy(
            items = unique,
            currentIndex = actualIndex,
            positionMs = 0L,
            mode = QueueMode.GHOST,
            ghostContext = GhostPlaybackContext(sourceKey, unique.map { it.trackId }),
            shuffleOrder = emptyList(),
        )
    }

    fun takeOverUserQueue(item: QueueItem, playNext: Boolean): Boolean {
        val wasGhost = state.value.mode == QueueMode.GHOST
        if (state.value.mode == QueueMode.GHOST) {
            val current = state.value.current
            state.value = state.value.copy(
                items = current?.let { listOf(it) } ?: emptyList(),
                currentIndex = if (current == null) -1 else 0,
                mode = QueueMode.USER,
                ghostContext = null,
                shuffleOrder = emptyList(),
            )
        }
        val changed = if (playNext) playNext(item.copy(origin = QueueEntryOrigin.MANUAL)) else add(item.copy(origin = QueueEntryOrigin.MANUAL))
        return changed || wasGhost
    }

    fun add(item: QueueItem): Boolean {
        if (state.value.items.any { it.trackId == item.trackId || it.entryId == item.entryId }) return false
        state.value = state.value.copy(
            items = state.value.items + item.copy(origin = QueueEntryOrigin.MANUAL),
            currentIndex = if (state.value.currentIndex < 0) 0 else state.value.currentIndex,
            shuffleOrder = state.value.shuffleOrder + item.entryId,
        )
        return true
    }

    fun addNext(item: QueueItem): Boolean {
        if (state.value.items.any { it.trackId == item.trackId || it.entryId == item.entryId }) return false
        val current = state.value
        val insertionIndex = if (current.currentIndex < 0) 0 else current.currentIndex + 1
        val inserted = item.copy(origin = QueueEntryOrigin.MANUAL)
        val items = current.items.toMutableList().also { it.add(insertionIndex.coerceAtMost(it.size), inserted) }
        state.value = current.copy(
            items = items,
            currentIndex = if (current.currentIndex < 0) 0 else current.currentIndex,
            shuffleOrder = current.shuffleOrder + inserted.entryId,
        )
        return true
    }

    /** Play Next moves an existing Track entry; it never creates a duplicate TrackID. */
    fun playNext(item: QueueItem): Boolean {
        val existingIndex = state.value.items.indexOfFirst { it.trackId == item.trackId }
        if (existingIndex < 0) return addNext(item)
        val current = state.value
        if (existingIndex == current.currentIndex) return false
        if (existingIndex == current.currentIndex + 1) return false
        val items = current.items.toMutableList()
        val moved = items.removeAt(existingIndex)
        val target = if (current.currentIndex < 0) 0 else (current.currentIndex + 1).coerceAtMost(items.size)
        items.add(target, moved)
        val currentId = current.current?.entryId
        state.value = current.copy(
            items = items,
            currentIndex = items.indexOfFirst { it.entryId == currentId },
            shuffleOrder = current.shuffleOrder,
        )
        return true
    }

    fun remove(index: Int): Boolean {
        val current = state.value
        if (index !in current.items.indices) return false
        val removedCurrent = index == current.currentIndex
        val items = current.items.toMutableList().also { it.removeAt(index) }
        val nextIndex = when {
            items.isEmpty() -> -1
            removedCurrent -> index.coerceAtMost(items.lastIndex)
            index < current.currentIndex -> current.currentIndex - 1
            else -> current.currentIndex
        }
        val removedId = current.items[index].entryId
        state.value = current.copy(
            items = items,
            currentIndex = nextIndex,
            positionMs = if (removedCurrent) 0L else current.positionMs,
            shuffleOrder = current.shuffleOrder.filterNot { it == removedId },
        )
        return true
    }

    fun clear() {
        state.value = state.value.copy(items = emptyList(), currentIndex = -1, positionMs = 0L, shuffleOrder = emptyList(), mode = QueueMode.USER, ghostContext = null)
    }

    fun reorder(from: Int, to: Int): Boolean {
        val current = state.value
        if (from !in current.items.indices || to !in current.items.indices || from == to) return false
        val items = current.items.toMutableList()
        val moved = items.removeAt(from)
        items.add(to, moved)
        val currentId = current.current?.trackId
        state.value = current.copy(items = items, currentIndex = items.indexOfFirst { it.trackId == currentId })
        return true
    }

    fun select(index: Int): Boolean {
        if (index !in state.value.items.indices) return false
        state.value = state.value.copy(currentIndex = index, positionMs = 0L)
        return true
    }

    fun setShuffle(enabled: Boolean) {
        val current = state.value
        val order = if (!enabled) emptyList() else {
            val currentId = current.current?.entryId
            val rest = current.items.map { it.entryId }.filterNot { it == currentId }.shuffled(Random)
            listOfNotNull(currentId) + rest
        }
        state.value = current.copy(shuffle = enabled, shuffleOrder = order)
    }

    fun setRepeat(mode: RepeatMode) {
        state.value = state.value.copy(repeat = mode)
    }

    fun setPosition(positionMs: Long) {
        state.value = state.value.copy(positionMs = positionMs.coerceAtLeast(0L))
    }

    fun syncCurrentIndex(index: Int, positionMs: Long) {
        if (index !in state.value.items.indices) return
        state.value = state.value.copy(currentIndex = index, positionMs = positionMs.coerceAtLeast(0L))
    }

    fun restore(restored: PlaybackQueueState) {
        val unique = restored.items.distinctBy { it.trackId }
        val ids = unique.map { it.entryId }.toSet()
        state.value = restored.copy(
            items = unique,
            currentIndex = restored.currentIndex.coerceIn(-1, unique.lastIndex),
            autoPlayOnRestore = false,
            shuffleOrder = restored.shuffleOrder.filter { it in ids },
        )
    }

    fun orderedForPlayback(): List<QueueItem> {
        val current = state.value
        if (!current.shuffle || current.shuffleOrder.isEmpty()) return current.items
        val byId = current.items.associateBy { it.entryId }
        return current.shuffleOrder.mapNotNull(byId::get) + current.items.filterNot { it.entryId in current.shuffleOrder }
    }

    fun playbackIndexForCurrent(): Int {
        val currentId = state.value.current?.entryId ?: return -1
        return orderedForPlayback().indexOfFirst { it.entryId == currentId }
    }

    fun next(): Int? {
        val current = state.value
        if (current.items.isEmpty()) return null
        if (current.repeat == RepeatMode.ONE) return current.currentIndex.takeIf { it >= 0 }
        val ordered = orderedForPlayback()
        val currentId = current.current?.entryId ?: return null
        val orderedIndex = ordered.indexOfFirst { it.entryId == currentId }
        val next = orderedIndex + 1
        val targetId = when {
            next < ordered.size -> ordered[next].entryId
            current.repeat == RepeatMode.ALL -> ordered.firstOrNull()?.entryId
            else -> null
        } ?: return null
        val target = current.items.indexOfFirst { it.entryId == targetId }
        state.value = current.copy(currentIndex = target, positionMs = 0L)
        return target
    }

    fun previous(): Int? {
        val current = state.value
        if (current.items.isEmpty()) return null
        val ordered = orderedForPlayback()
        val currentId = current.current?.entryId ?: return null
        val orderedIndex = ordered.indexOfFirst { it.entryId == currentId }
        val targetId = (orderedIndex - 1).takeIf { it >= 0 }?.let { ordered[it].entryId }
            ?: if (current.repeat == RepeatMode.ALL) ordered.lastOrNull()?.entryId else null
            ?: return null
        val target = current.items.indexOfFirst { it.entryId == targetId }
        state.value = current.copy(currentIndex = target, positionMs = 0L)
        return target
    }

    fun syncCurrentEntry(entryId: String?, positionMs: Long) {
        val index = state.value.items.indexOfFirst { it.entryId == entryId }
        if (index >= 0) syncCurrentIndex(index, positionMs)
    }
}

class PlaybackQueueStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("playback_queue", Context.MODE_PRIVATE)

    fun read(): PlaybackQueueState {
        val encoded = preferences.getString(KEY_ITEMS, "").orEmpty()
        val items = encoded.split('|').mapNotNull { token ->
            val fields = token.split(':')
            runCatching {
                when (fields.size) {
                    7 -> QueueItem(trackId = decode(fields[0]), entryId = decode(fields[1]), title = decode(fields[2]), artist = decode(fields[3]), album = decode(fields[4]), durationMs = fields[5].toLongOrNull(), origin = runCatching { QueueEntryOrigin.valueOf(fields[6]) }.getOrDefault(QueueEntryOrigin.MANUAL))
                    6 -> QueueItem(trackId = decode(fields[0]), entryId = decode(fields[1]), title = decode(fields[2]), artist = decode(fields[3]), album = decode(fields[4]), durationMs = fields[5].toLongOrNull())
                    5 -> QueueItem(trackId = decode(fields[0]), title = decode(fields[1]), artist = decode(fields[2]), album = decode(fields[3]), durationMs = fields[4].toLongOrNull())
                    else -> null
                }
            }.getOrNull()
        }
        val persistedTrackId = preferences.getString(KEY_CURRENT_TRACK, null)
        val persistedIndex = persistedTrackId?.let { id -> items.indexOfFirst { it.trackId == id }.takeIf { it >= 0 } }
            ?: preferences.getInt(KEY_CURRENT, if (items.isEmpty()) -1 else 0)
        return PlaybackQueueState(
            items = items,
            currentIndex = persistedIndex,
            shuffle = preferences.getBoolean(KEY_SHUFFLE, false),
            shuffleOrder = preferences.getString(KEY_SHUFFLE_ORDER, "").orEmpty().split(',').mapNotNull { it.takeIf(String::isNotBlank) },
            repeat = runCatching { RepeatMode.valueOf(preferences.getString(KEY_REPEAT, RepeatMode.OFF.name)!!) }.getOrDefault(RepeatMode.OFF),
            positionMs = preferences.getLong(KEY_POSITION, 0L),
            autoPlayOnRestore = false,
            mode = runCatching { QueueMode.valueOf(preferences.getString(KEY_MODE, QueueMode.USER.name)!!) }.getOrDefault(QueueMode.USER),
            ghostContext = preferences.getString(KEY_GHOST_SOURCE, null)?.let { source ->
                GhostPlaybackContext(source, preferences.getString(KEY_GHOST_IDS, "").orEmpty().split(',').filter(String::isNotBlank))
            },
        )
    }

    fun write(state: PlaybackQueueState) {
        val encoded = state.items.joinToString("|") {
            listOf(it.trackId, it.entryId, it.title, it.artist, it.album).joinToString(":") { value -> encode(value) } + ":${it.durationMs ?: ""}:${it.origin.name}"
        }
        preferences.edit()
            .putString(KEY_ITEMS, encoded)
            .putInt(KEY_CURRENT, state.currentIndex)
            .putString(KEY_CURRENT_TRACK, state.current?.trackId)
            .putBoolean(KEY_SHUFFLE, state.shuffle)
            .putString(KEY_SHUFFLE_ORDER, state.shuffleOrder.joinToString(","))
            .putString(KEY_REPEAT, state.repeat.name)
            .putLong(KEY_POSITION, state.positionMs)
            .putString(KEY_MODE, state.mode.name)
            .putString(KEY_GHOST_SOURCE, state.ghostContext?.sourceKey)
            .putString(KEY_GHOST_IDS, state.ghostContext?.trackIds?.joinToString(","))
            .apply()
    }

    private fun encode(value: String): String = PlaybackQueueCodec.encode(value)
    private fun decode(value: String): String = PlaybackQueueCodec.decode(value)

    private companion object {
        const val KEY_ITEMS = "items"
        const val KEY_CURRENT = "current"
        const val KEY_CURRENT_TRACK = "current_track"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_SHUFFLE_ORDER = "shuffle_order"
        const val KEY_REPEAT = "repeat"
        const val KEY_POSITION = "position"
        const val KEY_MODE = "mode"
        const val KEY_GHOST_SOURCE = "ghost_source"
        const val KEY_GHOST_IDS = "ghost_ids"
    }
}

object PlaybackQueueCodec {
    fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    fun decode(value: String): String = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}
