package com.theemrock.smoothmedia.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.media3.common.MediaItem
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.theemrock.smoothmedia.eq.AndroidEqualizerBackend
import com.theemrock.smoothmedia.eq.EqualizerBand
import com.theemrock.smoothmedia.eq.EqualizerProfileStore
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

enum class PlaybackStatus { IDLE, LOADING, READY, PLAYING, PAUSED, ENDED, ERROR }

data class PlaybackEngineState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val trackId: String? = null,
    val locationId: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null,
)

interface PlaybackEngine : AutoCloseable {
    val player: Player
    val state: PlaybackEngineState
    fun play(trackId: String)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun equalizerBands(): List<EqualizerBand> = emptyList()
    fun applyManualEqualizer(levels: Map<Short, Int>): Boolean = false
    fun setEqualizerEnabled(enabled: Boolean): Boolean = false
    override fun close()
}

class Media3PlaybackEngine(
    context: Context,
    private val locationsForTrack: (String) -> List<PlaybackLocation>,
) : PlaybackEngine {
    private val thread = HandlerThread("smooth-media-playback").apply { start() }
    private val handler = Handler(thread.looper)
    private val listeners = CopyOnWriteArrayList<(PlaybackEngineState) -> Unit>()
    private var currentLocations: List<PlaybackLocation> = emptyList()
    private var currentLocationIndex: Int = -1
    private val exoPlayer = ExoPlayer.Builder(context.applicationContext).setLooper(thread.looper).build()
    private val equalizerBackend = AndroidEqualizerBackend()
    private val equalizerProfile = EqualizerProfileStore(context)
    @Volatile
    override var state: PlaybackEngineState = PlaybackEngineState()
        private set

    override val player: Player get() = exoPlayer

    init {
        handler.post {
            exoPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            exoPlayer.setHandleAudioBecomingNoisy(true)
            exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val status = when (playbackState) {
                    Player.STATE_BUFFERING -> PlaybackStatus.LOADING
                    Player.STATE_READY -> if (exoPlayer.playWhenReady) PlaybackStatus.PLAYING else PlaybackStatus.READY
                    Player.STATE_ENDED -> PlaybackStatus.ENDED
                    else -> PlaybackStatus.IDLE
                }
                publish(state.copy(status = status, positionMs = exoPlayer.currentPosition, durationMs = exoPlayer.duration.coerceAtLeast(0L)))
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val trackId = mediaItem?.mediaMetadata?.extras?.getString(EXTRA_TRACK_ID)
                    ?: mediaItem?.mediaId
                if (trackId != null) {
                    currentLocations = locationsForTrack(trackId)
                        .filter { it.accessible && it.contentUri.startsWith("content://") }
                        .sortedWith(compareByDescending<PlaybackLocation> { it.format == PlaybackFormat.FLAC }.thenBy { it.locationId })
                    currentLocationIndex = currentLocations.indexOfFirst { it.contentUri == mediaItem?.localConfiguration?.uri.toString() }
                    publish(state.copy(trackId = trackId, locationId = currentLocations.getOrNull(currentLocationIndex)?.locationId))
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publish(state.copy(status = if (isPlaying) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED))
            }

            override fun onPlayerError(error: PlaybackException) {
                val nextIndex = currentLocationIndex + 1
                if (nextIndex in currentLocations.indices) {
                    currentLocationIndex = nextIndex
                    val fallback = currentLocations[nextIndex]
                    val currentMediaItem = exoPlayer.currentMediaItem
                    val replacement = MediaItem.Builder()
                        .setMediaId(currentMediaItem?.mediaId ?: fallback.trackId)
                        .setUri(Uri.parse(fallback.contentUri))
                        .apply { currentMediaItem?.let { setMediaMetadata(it.mediaMetadata) } }
                        .build()
                    exoPlayer.replaceMediaItem(exoPlayer.currentMediaItemIndex, replacement)
                    exoPlayer.prepare()
                    exoPlayer.seekTo(exoPlayer.currentMediaItemIndex, exoPlayer.currentPosition.coerceAtLeast(0L))
                    exoPlayer.playWhenReady = true
                    return
                }
                publish(state.copy(status = PlaybackStatus.ERROR, errorMessage = error.message ?: error.errorCodeName))
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (equalizerBackend.attach(audioSessionId) && equalizerProfile.isManualEnabled()) {
                    equalizerBackend.applyManual(equalizerProfile.readLevels())
                }
            }
            })
        }
    }

    fun <T> onPlayerThreadBlocking(block: () -> T): T {
        if (Thread.currentThread() == thread) return block()
        val completed = CountDownLatch(1)
        val result = AtomicReference<Result<T>>()
        handler.post {
            result.set(runCatching(block))
            completed.countDown()
        }
        check(completed.await(2, TimeUnit.SECONDS)) { "Playback thread did not respond" }
        return result.get().getOrThrow()
    }

    fun addListener(listener: (PlaybackEngineState) -> Unit) {
        listeners += listener
        listener(state)
    }

    fun removeListener(listener: (PlaybackEngineState) -> Unit) {
        listeners -= listener
    }

    override fun play(trackId: String) {
        handler.post {
            currentLocations = locationsForTrack(trackId)
                .filter { it.accessible && it.contentUri.startsWith("content://") }
                .sortedWith(compareByDescending<PlaybackLocation> { it.format == PlaybackFormat.FLAC }.thenBy { it.locationId })
            val location = PlaybackLocationSelector.select(currentLocations)
            if (location == null) {
                publish(PlaybackEngineState(status = PlaybackStatus.ERROR, trackId = trackId, errorMessage = "No accessible SAF location"))
                return@post
            }
            currentLocationIndex = currentLocations.indexOfFirst { it.locationId == location.locationId }
            startLocation(location, 0L)
        }
    }

    override fun pause() {
        handler.post { exoPlayer.pause() }
    }

    override fun resume() {
        handler.post { exoPlayer.play() }
    }

    override fun seekTo(positionMs: Long) {
        handler.post { exoPlayer.seekTo(positionMs.coerceAtLeast(0L)) }
    }

    override fun applyManualEqualizer(levels: Map<Short, Int>): Boolean = onPlayerThreadBlocking {
        equalizerProfile.writeLevels(levels)
        equalizerBackend.applyManual(levels)
    }

    override fun setEqualizerEnabled(enabled: Boolean): Boolean = onPlayerThreadBlocking {
        equalizerProfile.writeManualEnabled(enabled)
        equalizerBackend.setEnabled(enabled)
    }

    override fun equalizerBands(): List<EqualizerBand> = onPlayerThreadBlocking { equalizerBackend.bands() }

    override fun close() {
        val released = CountDownLatch(1)
        handler.post {
            exoPlayer.stop()
            equalizerBackend.close()
            exoPlayer.release()
            thread.quitSafely()
            released.countDown()
        }
        released.await(2, TimeUnit.SECONDS)
    }

    private fun publish(next: PlaybackEngineState) {
        state = next
        listeners.forEach { it(next) }
    }

    private fun startLocation(location: PlaybackLocation, positionMs: Long) {
        publish(PlaybackEngineState(status = PlaybackStatus.LOADING, trackId = location.trackId, locationId = location.locationId))
        exoPlayer.setMediaItem(MediaItem.Builder().setMediaId(location.trackId).setUri(Uri.parse(location.contentUri)).build())
        exoPlayer.prepare()
        if (positionMs > 0L) exoPlayer.seekTo(positionMs)
        exoPlayer.playWhenReady = true
    }
}

private const val EXTRA_TRACK_ID = "com.theemrock.smoothmedia.extra.TRACK_ID"
