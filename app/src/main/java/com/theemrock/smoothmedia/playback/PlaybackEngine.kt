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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.theemrock.smoothmedia.eq.AutoEqAudioProcessor
import com.theemrock.smoothmedia.eq.DynamicSession
import com.theemrock.smoothmedia.eq.EqMode
import com.theemrock.smoothmedia.eq.EqProfile
import com.theemrock.smoothmedia.eq.SongEqPlan
import com.theemrock.smoothmedia.eq.SongEqResolver
import com.theemrock.smoothmedia.eq.SongEqSource
import com.theemrock.smoothmedia.eq.EqualizerBand
import com.theemrock.smoothmedia.eq.EqualizerProfileStore
import com.theemrock.smoothmedia.eq.ManualEqBands
import com.theemrock.smoothmedia.eq.ParametricEq
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
    fun applyAutoEq(text: String?): Boolean = false
    fun songEqGainsDb(): List<Double>? = null
    fun songEqScanned(): Boolean = false
    override fun close()
}

internal const val AUTOEQ_PREF_NAME = "autoeq"
internal const val AUTOEQ_PREF_KEY = "profile_text"

class Media3PlaybackEngine(
    context: Context,
    private val locationsForTrack: (String) -> List<PlaybackLocation>,
    private val songEq: SongEqSource? = null,
    private val eqMode: () -> EqMode = { EqMode.MANUAL },
) : PlaybackEngine {
    private val thread = HandlerThread("smooth-media-playback").apply { start() }
    private val handler = Handler(thread.looper)
    private val listeners = CopyOnWriteArrayList<(PlaybackEngineState) -> Unit>()
    private var currentLocations: List<PlaybackLocation> = emptyList()
    private var currentLocationIndex: Int = -1
    private val autoEqProcessor = AutoEqAudioProcessor()
    private val autoEqPrefs = context.getSharedPreferences(AUTOEQ_PREF_NAME, Context.MODE_PRIVATE)
    private val exoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setLooper(thread.looper)
        .setRenderersFactory(object : DefaultRenderersFactory(context.applicationContext) {
            override fun buildAudioSink(
                audioContext: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(audioContext).setAudioProcessors(arrayOf(autoEqProcessor)).build()
        })
        .build()
    private val equalizerProfile = EqualizerProfileStore(context)
    private var autoEqProfile: EqProfile? = null
    private var songPlan: SongEqPlan = SongEqPlan.Legacy
    private var dynamic: DynamicSession? = null
    @Volatile private var liveGainsDb: List<Double>? = null
    @Volatile private var songScanned = false
    private val ticker = object : Runnable {
        override fun run() {
            val session = dynamic ?: return
            if (exoPlayer.isPlaying) applyLive(session.profileAt(exoPlayer.currentPosition, TICK_MS / 1000.0))
            handler.postDelayed(this, TICK_MS)
        }
    }
    @Volatile
    override var state: PlaybackEngineState = PlaybackEngineState()
        private set

    override val player: Player get() = exoPlayer

    init {
        autoEqProfile = autoEqPrefs.getString(AUTOEQ_PREF_KEY, null)?.let { ParametricEq.parseAutoEq(it) }
        refreshEqualizer()
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
                    applySongEq(trackId)
                }
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                dynamic?.reset()
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

    override fun applyManualEqualizer(levels: Map<Short, Int>): Boolean {
        equalizerProfile.writeLevels(levels)
        refreshEqualizer()
        return true
    }

    override fun setEqualizerEnabled(enabled: Boolean): Boolean {
        equalizerProfile.writeManualEnabled(enabled)
        refreshEqualizer()
        return true
    }

    override fun equalizerBands(): List<EqualizerBand> = ManualEqBands.bands(equalizerProfile.readLevels())

    override fun applyAutoEq(text: String?): Boolean {
        val saved = text?.takeIf { it.isNotBlank() }
        autoEqPrefs.edit().putString(AUTOEQ_PREF_KEY, saved).apply()
        autoEqProfile = saved?.let { ParametricEq.parseAutoEq(it) }
        refreshEqualizer()
        return true
    }

    override fun close() {
        val released = CountDownLatch(1)
        handler.post {
            exoPlayer.stop()
            exoPlayer.release()
            thread.quitSafely()
            released.countDown()
        }
        released.await(2, TimeUnit.SECONDS)
    }

    /** Single source for the processor: AutoEQ correction plus enabled manual bands. */
    private fun refreshEqualizer() {
        if (songPlan !is SongEqPlan.Legacy) return
        val auto = autoEqProfile
        val manual = ManualEqBands.peakBands(equalizerProfile.readLevels(), equalizerProfile.isManualEnabled())
        autoEqProcessor.setProfile(
            EqProfile(preampDb = (auto?.preampDb ?: 0.0) + ManualEqBands.preampDb(manual), bands = auto?.bands.orEmpty() + manual),
        )
    }

    /** Picks the song's EQ plan on the player thread; a failed lookup falls back to the legacy chain. */
    private fun applySongEq(trackId: String) {
        val source = songEq ?: return
        val data = runCatching { source.load(trackId) }.getOrNull()
        songScanned = data?.windows?.any { it.bandDb != null } == true
        val plan = data?.let { runCatching { SongEqResolver.resolve(eqMode(), it) }.getOrNull() } ?: SongEqPlan.Legacy
        songPlan = plan
        dynamic = (plan as? SongEqPlan.Dynamic)?.let(::DynamicSession)
        liveGainsDb = null
        when (plan) {
            SongEqPlan.Legacy -> refreshEqualizer()
            is SongEqPlan.Override -> applyLive(plan.profile)
            is SongEqPlan.Dynamic -> {
                handler.removeCallbacks(ticker)
                dynamic?.let { applyLive(it.profileAt(exoPlayer.currentPosition, TICK_MS / 1000.0)) }
                handler.postDelayed(ticker, TICK_MS)
            }
        }
    }

    private fun applyLive(profile: EqProfile) {
        liveGainsDb = profile.bands.map { it.gainDb }
        autoEqProcessor.setProfile(profile)
    }

    override fun songEqGainsDb(): List<Double>? = liveGainsDb

    override fun songEqScanned(): Boolean = songScanned

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
private const val TICK_MS = 200L
