package com.theemrock.smoothmedia.eq

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * Decodes any Android-supported audio file to mono float PCM for analysis.
 * shortcut: expects 16-bit PCM output (the decoder default); other encodings fail the track instead of misreading it.
 */
class MediaCodecPcmDecoder(private val context: Context) : PcmDecoder {
    override fun decode(uri: String, sink: (FloatArray, Int, Int) -> Unit) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val index = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            val codec = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                pump(extractor, codec, format.getInteger(MediaFormat.KEY_SAMPLE_RATE), format.getInteger(MediaFormat.KEY_CHANNEL_COUNT), sink)
            } finally {
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun pump(extractor: MediaExtractor, codec: MediaCodec, startRate: Int, startChannels: Int, sink: (FloatArray, Int, Int) -> Unit) {
        val info = MediaCodec.BufferInfo()
        var rate = startRate
        var channels = startChannels
        var mono = FloatArray(0)
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            // Feed every free input buffer and drain every ready output without waiting; one buffer per
            // round trip with a 10 ms timeout made decoding latency-bound.
            var fed = false
            while (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(0)
                if (inIndex < 0) break
                val size = extractor.readSampleData(requireNotNull(codec.getInputBuffer(inIndex)), 0)
                if (size < 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                    extractor.advance()
                }
                fed = true
            }
            var drainWait = if (fed) 0L else TIMEOUT_US
            while (!outputDone) {
                val outIndex = codec.dequeueOutputBuffer(info, drainWait)
                drainWait = 0L
                if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out = codec.outputFormat
                    rate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    val encoding = if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) out.getInteger(MediaFormat.KEY_PCM_ENCODING) else ENCODING_PCM_16BIT
                    if (encoding != ENCODING_PCM_16BIT) throw UnsupportedOperationException("PCM encoding $encoding")
                } else if (outIndex >= 0) {
                    val buffer = requireNotNull(codec.getOutputBuffer(outIndex))
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val shorts = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val frames = shorts.remaining() / channels
                    if (mono.size < frames) mono = FloatArray(frames)
                    for (f in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until channels) sum += shorts.get()
                        mono[f] = sum / channels / 32_768f
                    }
                    if (frames > 0) sink(mono, frames, rate)
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val ENCODING_PCM_16BIT = 2
    }
}
