package com.theemrock.smoothmedia.eq

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class AutoEqAudioProcessorTest {
    @Test
    fun minusSixDbPreampHalvesPcm16Sample() {
        val processor = AutoEqAudioProcessor()
        processor.setProfile(EqProfile(preampDb = -6.0206, bands = emptyList()))
        processor.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_16BIT))

        val input = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(16_000).apply { flip() }
        processor.queueInput(input)
        val buffer = processor.getOutput().order(ByteOrder.LITTLE_ENDIAN)
        assertTrue("no output bytes, remaining=${buffer.remaining()}", buffer.remaining() >= 2)
        val output = buffer.getShort().toInt()

        assertTrue("expected about 8000, was $output", abs(output - 8_000) <= 2)
    }

    @Test
    fun emptyProfilePassesSamplesThrough() {
        val processor = AutoEqAudioProcessor()
        processor.setProfile(EqProfile(preampDb = 0.0, bands = emptyList()))
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        val input = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putShort(16_000).putShort(-16_000).apply { flip() }
        processor.queueInput(input)
        val output = processor.getOutput().order(ByteOrder.LITTLE_ENDIAN)
        assertTrue(abs(output.getShort() - 16_000) <= 2)
        assertTrue(abs(output.getShort() + 16_000) <= 2)
    }

    @Test
    fun missingProfileDoesNotRejectHiResPcm() {
        val processor = AutoEqAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(96_000, 2, C.ENCODING_PCM_24BIT))
        assertFalse(processor.isActive)
    }

    @Test
    fun activeProfileStaysPassiveForUnsupportedEncoding() {
        val processor = AutoEqAudioProcessor()
        processor.setProfile(EqProfile(preampDb = -3.0, bands = emptyList()))
        processor.configure(AudioProcessor.AudioFormat(96_000, 2, C.ENCODING_PCM_32BIT))
        assertFalse(processor.isActive)
    }
}
