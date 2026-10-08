package com.theemrock.smoothmedia.p01x

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P01xDiscoveryMetadataSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun grantAudioReadPermissionForProbe() {
        if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
        }
    }

    @Test
    fun discoveryAndMetadata_reportRealMusicArchiveWithoutWriting() {
        val discovered = MediaStoreMusicDiscovery(context.contentResolver).discover()
        val mp3 = discovered.filter { it.formatLabel == "MP3" }
        val flac = discovered.filter { it.formatLabel == "FLAC" }

        Log.i(TAG, "DISCOVERY|total=${discovered.size}|mp3=${mp3.size}|flac=${flac.size}")
        mp3.firstOrNull()?.let { Log.i(TAG, "AUDIO_CANDIDATE|MP3|${it.displayName}|${it.contentUri}") }
        flac.firstOrNull()?.let { Log.i(TAG, "AUDIO_CANDIDATE|FLAC|${it.displayName}|${it.contentUri}") }

        assertTrue("Music/ altında MP3 veya FLAC bulunamadı", discovered.isNotEmpty())
        assertTrue(discovered.all { MusicProbePolicy.isMusicPath(it.relativePath) })
        assertTrue(discovered.all { MusicProbePolicy.isSupportedAudio(it.mimeType, it.displayName) })

        val sample = (mp3.take(SAMPLE_PER_FORMAT) + flac.take(SAMPLE_PER_FORMAT))
            .distinctBy { it.contentUri }
        val reader = AndroidAudioMetadataReader(context)
        var metadataAvailable = 0
        var metadataMissing = 0
        var metadataIncomplete = 0
        var metadataUnsupported = 0
        var artworkAvailable = 0
        var artworkMissing = 0
        var artworkUnsupported = 0
        var genreIncomplete = 0

        sample.forEach { audio ->
            val metadata = reader.read(audio)
            val fields: List<ProbeValue<*>> = listOf(
                metadata.title,
                metadata.artist,
                metadata.album,
                metadata.albumArtist,
                metadata.year,
                metadata.trackNumber,
                metadata.discNumber,
                metadata.durationMs,
                metadata.bitrate,
                metadata.sampleRate,
                metadata.channels,
            )
            metadataAvailable += fields.count { it.status == ProbeStatus.AVAILABLE }
            metadataMissing += fields.count { it.status == ProbeStatus.MISSING }
            metadataIncomplete += fields.count { it.status == ProbeStatus.INCOMPLETE }
            metadataUnsupported += fields.count { it.status == ProbeStatus.UNSUPPORTED }
            artworkAvailable += if (metadata.artwork.status == ProbeStatus.AVAILABLE) 1 else 0
            artworkMissing += if (metadata.artwork.status == ProbeStatus.MISSING) 1 else 0
            artworkUnsupported += if (metadata.artwork.status == ProbeStatus.UNSUPPORTED) 1 else 0
            genreIncomplete += if (metadata.genres.status == ProbeStatus.INCOMPLETE) 1 else 0
        }

        Log.i(
            TAG,
            "METADATA|sample=${sample.size}|available=$metadataAvailable|missing=$metadataMissing|" +
                "incomplete=$metadataIncomplete|unsupported=$metadataUnsupported",
        )
        Log.i(
            TAG,
            "ARTWORK|available=$artworkAvailable|missing=$artworkMissing|unsupported=$artworkUnsupported",
        )
        Log.i(TAG, "GENRE|incomplete=$genreIncomplete|policy=raw-platform-value-preserved")
    }

    private companion object {
        const val TAG = "SmoothMediaP01X"
        const val SAMPLE_PER_FORMAT = 20
    }
}
