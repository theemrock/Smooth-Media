package com.theemrock.smoothmedia.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.theemrock.smoothmedia.eq.EqualizerBand

data class PlayableQueueProjection(
    val items: List<QueueItem>,
    val locations: List<PlaybackLocation>,
    val startIndex: Int,
)

object PlaybackQueueProjection {
    fun project(
        items: List<QueueItem>,
        locations: Map<String, PlaybackLocation>,
        startIndex: Int,
    ): PlayableQueueProjection {
        val currentEntryId = items.getOrNull(startIndex)?.entryId
        val playable = items.mapNotNull { item ->
            locations[item.entryId]?.takeIf { it.accessible }?.let { item to it }
        }
        val playableIndex = playable.indexOfFirst { it.first.entryId == currentEntryId }.takeIf { it >= 0 } ?: 0
        return PlayableQueueProjection(
            items = playable.map { it.first },
            locations = playable.map { it.second },
            startIndex = playableIndex.coerceIn(0, (playable.size - 1).coerceAtLeast(0)),
        )
    }
}

class PlaybackSessionClient(
    context: Context,
    private val onState: (PlaybackEngineState) -> Unit,
    private val onQueueState: (String?, Long) -> Unit = { _, _ -> },
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val future = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
    ).buildAsync()
    private var controller: MediaController? = null
    private var pendingLocation: PlaybackLocation? = null
    private var queueItems: List<QueueItem> = emptyList()
    private var pendingQueue: Triple<List<QueueItem>, Map<String, PlaybackLocation>, Int>? = null
    private var pendingQueuePlay: Boolean = false
    private var repeatModeSetting: RepeatMode = RepeatMode.OFF
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressTick = object : Runnable {
        override fun run() {
            if (controller != null) publish()
            progressHandler.postDelayed(this, 500L)
        }
    }

    init {
        future.addListener({
            runCatching { future.get() }.onSuccess { connected ->
                controller = connected
                connected.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) = publish()
                    override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) = publish()
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = publish()
                    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                        reconcileQueueItems()
                        publish()
                    }
                })
                publish()
                pendingQueue?.let { (items, locations, startIndex) -> applyQueue(items, locations, startIndex, 0L, false) }
                pendingQueuePlay = false
                progressHandler.post(progressTick)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    fun play(location: PlaybackLocation) {
        pendingLocation = location
        controller?.apply {
            setMediaItem(MediaItem.Builder().setMediaId(location.trackId).setUri(location.contentUri).build())
            prepare()
            play()
        }
    }

    fun play(item: QueueItem, location: PlaybackLocation) {
        pendingLocation = location
        queueItems = listOf(item)
        controller?.apply {
            setMediaItem(mediaItem(item, location))
            prepare()
            play()
        }
    }

    fun pause() { controller?.pause() }
    fun resume() { controller?.play() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs.coerceAtLeast(0L)) }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    /** Domain shuffleOrder is projected into Media3 playlist order; Media3 must not reshuffle it. */
    fun setShuffle(@Suppress("UNUSED_PARAMETER") enabled: Boolean) { controller?.shuffleModeEnabled = false }
    fun setRepeat(mode: RepeatMode) {
        repeatModeSetting = mode
        controller?.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    fun equalizerBands(): List<EqualizerBand> = PlaybackEngineRegistry.engine?.equalizerBands().orEmpty()

    fun applyManualEqualizer(levels: Map<Short, Int>): Boolean =
        PlaybackEngineRegistry.engine?.applyManualEqualizer(levels) == true

    fun setEqualizerEnabled(enabled: Boolean): Boolean =
        PlaybackEngineRegistry.engine?.setEqualizerEnabled(enabled) == true

    fun loadQueue(items: List<QueueItem>, locations: Map<String, PlaybackLocation>, startIndex: Int = 0, startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        pendingQueue = Triple(items, locations, startIndex)
        pendingQueuePlay = playWhenReady
        applyQueue(items, locations, startIndex, startPositionMs, playWhenReady)
    }

    private fun applyQueue(items: List<QueueItem>, locations: Map<String, PlaybackLocation>, startIndex: Int, startPositionMs: Long, playWhenReady: Boolean) {
        val projection = PlaybackQueueProjection.project(items, locations, startIndex)
        if (projection.items.isEmpty()) return
        queueItems = projection.items
        controller?.apply {
            setMediaItems(
                projection.items.zip(projection.locations).map { (item, location) -> mediaItem(item, location) },
                projection.startIndex,
                startPositionMs.coerceAtLeast(0L),
            )
            repeatMode = when (repeatModeSetting) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            }
            shuffleModeEnabled = false
            prepare()
            if (playWhenReady) play() else pause()
        }
    }

    fun playFromQueue(index: Int) { controller?.apply { seekToDefaultPosition(index); play() } }
    fun clearQueue() {
        queueItems = emptyList()
        controller?.clearMediaItems()
    }
    fun removeQueueEntry(index: Int) { controller?.removeMediaItem(index); reconcileQueueItems() }
    fun moveQueueEntry(from: Int, to: Int) { controller?.moveMediaItem(from, to); reconcileQueueItems() }
    fun currentQueueItems(): List<QueueItem> = queueItems

    private fun publish() {
        val player = controller ?: return
        onState(
            PlaybackEngineState(
                status = when {
                    player.playerError != null -> PlaybackStatus.ERROR
                    player.isPlaying -> PlaybackStatus.PLAYING
                    player.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.LOADING
                    player.playbackState == Player.STATE_ENDED -> PlaybackStatus.ENDED
                    else -> PlaybackStatus.PAUSED
                },
                trackId = queueItems.getOrNull(player.currentMediaItemIndex)?.trackId ?: player.currentMediaItem?.mediaId,
                positionMs = player.currentPosition,
                durationMs = player.duration.coerceAtLeast(0L),
                errorMessage = player.playerError?.message,
            ),
        )
        onQueueState(player.currentMediaItem?.mediaId, player.currentPosition)
    }

    private fun reconcileQueueItems() {
        val player = controller ?: return
        val byEntryId = queueItems.associateBy { it.entryId }
        queueItems = (0 until player.mediaItemCount).mapNotNull { index ->
            byEntryId[player.getMediaItemAt(index).mediaId]
        }
    }

    private fun mediaItem(item: QueueItem, location: PlaybackLocation): MediaItem = MediaItem.Builder()
        .setMediaId(item.entryId)
        .setUri(location.contentUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.artist)
                .setAlbumTitle(item.album)
                .setDurationMs(item.durationMs)
                .setIsPlayable(true)
                .setExtras(Bundle().apply {
                    putString(EXTRA_TRACK_ID, item.trackId)
                    putString(EXTRA_QUEUE_ENTRY_ID, item.entryId)
                })
                .build(),
        )
        .build()

    private companion object {
        const val EXTRA_TRACK_ID = "com.theemrock.smoothmedia.extra.TRACK_ID"
        const val EXTRA_QUEUE_ENTRY_ID = "com.theemrock.smoothmedia.extra.QUEUE_ENTRY_ID"
    }

    override fun close() {
        progressHandler.removeCallbacks(progressTick)
        MediaController.releaseFuture(future)
        controller = null
    }
}
