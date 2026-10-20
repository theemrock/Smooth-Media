package com.theemrock.smoothmedia.catalog.scan

import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.discovery.AudioFormat
import com.theemrock.smoothmedia.discovery.DiscoveredAudioFile
import com.theemrock.smoothmedia.discovery.FullFileHashResult
import com.theemrock.smoothmedia.discovery.HashCancellation
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParallelHashStageTest {
    @Test
    fun hashesAtMostTwoFilesAndConsumesInDiscoveryOrder() {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val completionOrder = Collections.synchronizedList(mutableListOf<String>())
        val files = listOf(file("one"), file("two"), file("three"))
        val stage = ParallelHashStage(
            files = files,
            shouldHash = { true },
            status = { ScanSessionStatus.RUNNING },
            hash = { current, _ ->
                val now = active.incrementAndGet()
                maximum.updateAndGet { old -> maxOf(old, now) }
                if (current.documentId == "one") Thread.sleep(40)
                completionOrder += current.documentId
                active.decrementAndGet()
                success(current.documentId)
            },
        )

        val consumed = files.map { (stage.next()!!.second as ParallelHashStage.Result.Hashed).hashResult }

        assertEquals(listOf("one", "two", "three"), consumed.map { (it as FullFileHashResult.Success).digest })
        assertTrue(maximum.get() <= 2)
        assertTrue(completionOrder.indexOf("two") < completionOrder.indexOf("one"))
        stage.close()
    }

    @Test
    fun cancellationClosesActiveStreamsBeforeWorkerShutdownReturns() {
        val started = CountDownLatch(1)
        val workerExited = CountDownLatch(1)
        val stream = TrackingStream()
        var currentStatus = ScanSessionStatus.RUNNING
        val stage = ParallelHashStage(
            files = listOf(file("one"), file("two")),
            shouldHash = { true },
            status = { currentStatus },
            hash = { _, cancellation ->
                cancellation.onStreamOpened(stream)
                started.countDown()
                while (!cancellation.isCancelled()) Thread.yield()
                workerExited.countDown()
                FullFileHashResult.Cancelled
            },
        )

        stage.start()
        assertTrue(started.await(1, TimeUnit.SECONDS))
        currentStatus = ScanSessionStatus.CANCELLED
        assertEquals(ParallelHashStage.Result.Cancelled, stage.next()!!.second)
        stage.close()

        assertTrue(workerExited.await(1, TimeUnit.SECONDS))
        assertTrue(stream.closed)
        assertFalse(stage.hasLiveWorkers())
    }

    @Test
    fun skippedCompletedFilesDoNotHashAndStillPreserveOrder() {
        val hashed = mutableListOf<String>()
        val files = listOf(file("one"), file("two"))
        val stage = ParallelHashStage(
            files = files,
            shouldHash = { it.documentId != "one" },
            status = { ScanSessionStatus.RUNNING },
            hash = { current, _ -> hashed += current.documentId; success(current.documentId) },
        )

        assertEquals(ParallelHashStage.Result.Skipped, stage.next()!!.second)
        assertEquals("two", ((stage.next()!!.second as ParallelHashStage.Result.Hashed).hashResult as FullFileHashResult.Success).digest)
        assertEquals(listOf("two"), hashed)
        stage.close()
    }

    private fun success(id: String) = FullFileHashResult.Success("SHA-256", 1, id, 1)

    private fun file(id: String) = DiscoveredAudioFile(
        sourceId = "source",
        uri = "content://source/$id.mp3",
        documentId = id,
        displayName = "$id.mp3",
        format = AudioFormat.MP3,
        mimeType = "audio/mpeg",
        sizeBytes = 1,
        modifiedAtEpochMs = 1,
    )

    private class TrackingStream : InputStream() {
        var closed = false

        override fun read(): Int = -1

        override fun close() {
            closed = true
        }
    }
}
