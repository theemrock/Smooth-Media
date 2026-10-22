package com.theemrock.smoothmedia.metadata

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Id309RealFlacRoundTripDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val adapter = KyantTagLibAdapter()
    private val parser = FlacMetadataParser()
    private val temporaryFiles = mutableListOf<File>()

    @Before
    fun prepareIsolatedCopies() {
        val assetBytes = instrumentation.context.assets.open(FIXTURE_ASSET).use { it.readBytes() }
        assertTrue("Fixture must not be empty", assetBytes.isNotEmpty())
    }

    @After
    fun cleanupIsolatedCopies() {
        temporaryFiles.forEach { file -> assertTrue("Temporary fixture cleanup failed: $file", !file.exists() || file.delete()) }
        temporaryFiles.clear()
    }

    @Test
    fun artistChangeAndNoOpPreserveDecodedAudioAndPictures() {
        val artistFile = copyAssetToTargetCache("id3-09-artist")
        val noOpFile = copyAssetToTargetCache("id3-09-noop")

        val artistBefore = inspectAndDecode("artist-before", artistFile)
        val noOpBefore = inspectAndDecode("noop-before", noOpFile)
        assertEquals("Fixture must contain two pictures", 2, artistBefore.inspection.pictures.size)
        assertEquals(artistBefore.inspection.pictures, noOpBefore.inspection.pictures)
        assertEquals(artistBefore.inspection.audioSha256, noOpBefore.inspection.audioSha256)

        val artistSnapshot = readSnapshot(artistFile)
        val artistChange = FileTagChange(
            properties = artistSnapshot.properties + (ARTIST_KEY to listOf(UPDATED_ARTIST)),
            pictures = null,
        )
        val artistWrite = writeWithNativeAdapter(artistFile, artistChange)
        assertTrue("Native ARTIST save failed: $artistWrite", artistWrite is FileTagWriteResult.Written)
        val artistAfterSnapshot = readSnapshot(artistFile)
        assertEquals(UPDATED_ARTIST, artistAfterSnapshot.properties[ARTIST_KEY]?.single())

        val noOpSnapshot = readSnapshot(noOpFile)
        val noOpWrite = writeWithNativeAdapter(noOpFile, FileTagChange(noOpSnapshot.properties, pictures = null))
        assertTrue("Native no-op save failed: $noOpWrite", noOpWrite is FileTagWriteResult.Written)

        val artistAfter = inspectAndDecode("artist-after", artistFile)
        val noOpAfter = inspectAndDecode("noop-after", noOpFile)
        assertEquals(
            FlacVerificationResult.Preserved,
            MetadataArtifactVerification.compare(
                MetadataArtifactVerification.summarize(artistBefore.inspection),
                MetadataArtifactVerification.summarize(artistAfter.inspection),
            ),
        )
        assertEquals(
            FlacVerificationResult.Preserved,
            MetadataArtifactVerification.compare(
                MetadataArtifactVerification.summarize(noOpBefore.inspection),
                MetadataArtifactVerification.summarize(noOpAfter.inspection),
            ),
        )
        assertEquals(UPDATED_ARTIST, readSnapshot(artistFile).properties[ARTIST_KEY]?.single())
        android.util.Log.i(TAG, "ID3_09_NATIVE|artist=PASS|noop=PASS|audio=PASS|pictures=PASS|decoder=PASS")
    }

    private fun copyAssetToTargetCache(label: String): File {
        val file = File(targetContext.cacheDir, "$label-${System.nanoTime()}.flac")
        instrumentation.context.assets.open(FIXTURE_ASSET).use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        temporaryFiles += file
        assertEquals(targetContext.cacheDir.canonicalPath, file.parentFile?.canonicalPath)
        return file
    }

    private fun readSnapshot(file: File): FileTagSnapshot =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            adapter.read(descriptor).getOrThrow()
        }

    private fun writeWithNativeAdapter(file: File, change: FileTagChange): FileTagWriteResult =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { descriptor ->
            adapter.write(descriptor, change)
        }

    private fun inspectAndDecode(label: String, file: File): DecodedInspection {
        val parsed = parser.parse(file.toPath())
        assertTrue("$label parser rejected fixture: $parsed", parsed is FlacParseResult.Success)
        val inspection = (parsed as FlacParseResult.Success).inspection
        val decoded = decode(file)
        assertTrue("$label decoder produced no PCM", decoded.decodedBytes > 0)
        assertTrue("$label duration is shorter than one second: ${decoded.durationUs}", decoded.durationUs >= 1_000_000L)
        android.util.Log.i(TAG, "ID3_09_DECODE|label=$label|durationUs=${decoded.durationUs}|pcmBytes=${decoded.decodedBytes}|audioSha=${inspection.audioSha256}|pictures=${inspection.pictures}")
        return DecodedInspection(inspection, decoded)
    }

    private fun decode(file: File): DecodeResult {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            assertNotNull("No audio track in FLAC fixture", trackIndex)
            extractor.selectTrack(trackIndex!!)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
            assertNotNull("Audio MIME missing", mime)
            codec = MediaCodec.createDecoderByType(mime!!)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var decodedBytes = 0L
            val deadline = SystemClock.elapsedRealtime() + 20_000L
            while (!outputDone && SystemClock.elapsedRealtime() < deadline) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(input, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        decodedBytes += info.size.toLong()
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            assertTrue("Decoder did not reach EOS", outputDone)
            return DecodeResult(format.getLong(MediaFormat.KEY_DURATION, 0L), decodedBytes)
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }

    private data class DecodedInspection(val inspection: FlacInspection, val decoded: DecodeResult)
    private data class DecodeResult(val durationUs: Long, val decodedBytes: Long)

    private companion object {
        const val FIXTURE_ASSET = "id3_native_roundtrip_fixture.flac"
        const val ARTIST_KEY = "ARTIST"
        const val UPDATED_ARTIST = "ID3-09 Native Artist"
        const val TIMEOUT_US = 10_000L
        const val TAG = "SmoothMediaId309"
    }
}
