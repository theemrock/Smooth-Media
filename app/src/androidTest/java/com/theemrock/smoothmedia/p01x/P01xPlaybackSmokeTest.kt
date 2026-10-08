package com.theemrock.smoothmedia.p01x

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P01xPlaybackSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun playMp3ForFiveSeconds_whenExplicitlyEnabled() {
        runExplicitPlaybackSmoke("MP3")
    }

    @Test
    fun playFlacForFiveSeconds_whenExplicitlyEnabled() {
        runExplicitPlaybackSmoke("FLAC")
    }

    private fun runExplicitPlaybackSmoke(format: String) {
        assumeTrue(
            "Ses smoke testi yalnız -e $AUDIO_ARGUMENT true ile çalıştırılır.",
            InstrumentationRegistry.getArguments().getString(AUDIO_ARGUMENT) == "true",
        )
        grantAudioReadPermission()
        val audio = MediaStoreMusicDiscovery(context.contentResolver)
            .discover()
            .firstOrNull { it.formatLabel == format }
        assumeNotNull("$format dosyası bulunamadı", audio)
        audio ?: return

        val ready = CountDownLatch(1)
        val error = AtomicReference<PlaybackException?>()
        var player: ExoPlayer? = null

        try {
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(context).build().apply {
                    volume = PROBE_VOLUME
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) ready.countDown()
                        }

                        override fun onPlayerError(playbackError: PlaybackException) {
                            error.set(playbackError)
                            ready.countDown()
                        }
                    })
                    setMediaItem(MediaItem.fromUri(audio.contentUri))
                    prepare()
                    play()
                }
            }

            assertTrue("$format Media3 READY zaman aşımı", ready.await(20, TimeUnit.SECONDS))
            error.get()?.let { throw AssertionError("$format playback hatası", it) }
            Log.i(
                TAG,
                "PLAYBACK_START|format=$format|name=${audio.displayName}|uri=${audio.contentUri}|" +
                    "seconds=$PLAYBACK_SECONDS|volume=$PROBE_VOLUME",
            )
            Thread.sleep(TimeUnit.SECONDS.toMillis(PLAYBACK_SECONDS))
            val positionMs = AtomicReference(0L)
            instrumentation.runOnMainSync { positionMs.set(player?.currentPosition ?: 0L) }
            assertTrue("$format playback ilerlemedi", positionMs.get() > 0L)
            Log.i(TAG, "PLAYBACK_PASS|format=$format|positionMs=${positionMs.get()}")
        } finally {
            instrumentation.runOnMainSync {
                player?.stop()
                player?.release()
                player = null
            }
        }
    }

    private fun grantAudioReadPermission() {
        if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
        }
    }

    private companion object {
        const val TAG = "SmoothMediaP01X"
        const val AUDIO_ARGUMENT = "p01xAudio"
        const val PLAYBACK_SECONDS = 5L
        const val PROBE_VOLUME = 0.1f
    }
}
