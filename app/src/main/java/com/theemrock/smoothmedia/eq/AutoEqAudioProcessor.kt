package com.theemrock.smoothmedia.eq

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Applies an AutoEQ profile (preamp plus peaking biquads) to PCM 16-bit or float audio.
 * shortcut: per-sample Kotlin loop, no SIMD; revisit only if playback stutters on device.
 */
class AutoEqAudioProcessor : BaseAudioProcessor() {
    @Volatile private var profile: EqProfile? = null
    @Volatile private var profileChanged = false
    private var sections: List<Biquad> = emptyList()
    private var preampGain = 1.0
    // Set in onConfigure: inputAudioFormat is only updated on flush, so it may still be unset in queueInput.
    private var configured: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var channels = 1
    private var isFloat = false
    // Per section and channel: x1, x2, y1, y2 (direct form I history).
    private var state: Array<Array<DoubleArray>> = emptyArray()

    fun setProfile(next: EqProfile?) {
        profile = next
        profileChanged = true
    }

    override fun isActive(): Boolean {
        val current = profile ?: return false
        return super.isActive() && (current.bands.isNotEmpty() || current.preampDb != 0.0)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        configured = inputAudioFormat
        rebuild(inputAudioFormat)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (profileChanged) rebuild(configured)
        val frameBytes = channels * (if (isFloat) 4 else 2)
        val frames = inputBuffer.remaining() / frameBytes
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val out = replaceOutputBuffer(frames * frameBytes).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) {
            for (c in 0 until channels) {
                var y = (if (isFloat) inputBuffer.getFloat().toDouble() else inputBuffer.getShort() / 32768.0) * preampGain
                for (s in sections.indices) {
                    val f = sections[s]
                    val h = state[s][c]
                    val next = f.b0 * y + f.b1 * h[0] + f.b2 * h[1] - f.a1 * h[2] - f.a2 * h[3]
                    h[1] = h[0]
                    h[0] = y
                    h[3] = h[2]
                    h[2] = next
                    y = next
                }
                val clamped = y.coerceIn(-1.0, 1.0)
                if (isFloat) out.putFloat(clamped.toFloat()) else out.putShort((clamped * 32767.0).roundToInt().toShort())
            }
        }
        out.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) = resetHistory()

    override fun onReset() = resetHistory()

    private fun rebuild(format: AudioProcessor.AudioFormat) {
        val current = profile
        val rate = format.sampleRate.toDouble()
        channels = format.channelCount
        isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        sections = current?.bands?.map { ParametricEq.peaking(it, rate) }.orEmpty()
        preampGain = 10.0.pow((current?.preampDb ?: 0.0) / 20.0)
        state = Array(sections.size) { Array(channels) { DoubleArray(4) } }
        profileChanged = false
    }

    private fun resetHistory() {
        state.forEach { section -> section.forEach { it.fill(0.0) } }
    }
}
