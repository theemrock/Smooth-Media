package com.theemrock.smoothmedia.discovery

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FullFileHashTest {
    @Test
    fun identicalBytesProduceIdenticalSha256() {
        val first = StreamingSha256Hasher().hash(ByteArrayInputStream(DATA))
        val second = StreamingSha256Hasher().hash(ByteArrayInputStream(DATA))

        assertEquals(first, second)
        assertTrue(first is FullFileHashResult.Success)
        assertEquals(
            "8c12334fb59fef20d32dbb350f0659d7bc6cb5c5dfa45e04c741db364a102214",
            (first as FullFileHashResult.Success).digest,
        )
    }

    @Test
    fun differentBytesProduceDifferentSha256() {
        val first = StreamingSha256Hasher().hash(ByteArrayInputStream(DATA))
        val second = StreamingSha256Hasher().hash(ByteArrayInputStream(DATA + 1))

        assertNotEquals(first, second)
    }

    @Test
    fun largeInputUsesFixedReadBuffer() {
        val input = TrackingInputStream(ByteArray(2 * 1024 * 1024) { it.toByte() })

        val result = StreamingSha256Hasher(chunkSizeBytes = 4096).hash(input)

        assertTrue(result is FullFileHashResult.Success)
        assertTrue(input.maximumRequestedRead <= 4096)
        assertTrue(input.readCalls > 1)
    }

    @Test
    fun cancellationReturnsControlledResultWithoutCompletingHash() {
        val result = StreamingSha256Hasher().hash(
            ByteArrayInputStream(DATA),
            cancellation = HashCancellation { true },
        )

        assertEquals(FullFileHashResult.Cancelled, result)
    }

    @Test
    fun readFailureReturnsControlledResult() {
        val result = StreamingSha256Hasher().hash(FailingInputStream())

        assertEquals(FullFileHashResult.ReadFailed, result)
    }

    @Test
    fun unexpectedReadFailureReturnsControlledResult() {
        val result = StreamingSha256Hasher().hash(UnexpectedFailingInputStream())

        assertEquals(FullFileHashResult.ReadFailed, result)
    }

    private class TrackingInputStream(
        private val bytes: ByteArray,
    ) : InputStream() {
        private var offset = 0
        var maximumRequestedRead: Int = 0
            private set
        var readCalls: Int = 0
            private set

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            maximumRequestedRead = maxOf(maximumRequestedRead, length)
            readCalls += 1
            if (this.offset == bytes.size) return -1
            val count = minOf(length, bytes.size - this.offset)
            bytes.copyInto(buffer, offset, this.offset, this.offset + count)
            this.offset += count
            return count
        }

        override fun read(): Int = error("single-byte reads are not allowed")
    }

    private class FailingInputStream : InputStream() {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            throw IOException("synthetic read failure")
        }

        override fun read(): Int = throw IOException("synthetic read failure")
    }

    private class UnexpectedFailingInputStream : InputStream() {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            throw IllegalStateException("synthetic provider failure")
        }

        override fun read(): Int = throw IllegalStateException("synthetic provider failure")
    }

    private companion object {
        val DATA = "smooth-media".toByteArray()
    }
}
