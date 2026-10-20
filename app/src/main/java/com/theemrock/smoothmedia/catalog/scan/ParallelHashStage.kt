package com.theemrock.smoothmedia.catalog.scan

import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.discovery.DiscoveredAudioFile
import com.theemrock.smoothmedia.discovery.FullFileHashResult
import com.theemrock.smoothmedia.discovery.HashCancellation
import java.io.InputStream
import java.util.ArrayDeque
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

/**
 * Hashes at most two discovery entries ahead while exposing results in discovery order.
 * It owns the worker lifecycle and never leaves an unbounded executor queue behind.
 */
internal class ParallelHashStage(
    private val files: List<DiscoveredAudioFile>,
    private val shouldHash: (DiscoveredAudioFile) -> Boolean,
    private val status: () -> ScanSessionStatus?,
    private val hash: (DiscoveredAudioFile, HashCancellation) -> FullFileHashResult,
    private val workerCount: Int = MAX_WORKERS,
    private val workerShutdownTimeoutMs: Long = WORKER_SHUTDOWN_TIMEOUT_MS,
) : AutoCloseable {
    init {
        require(workerCount == MAX_WORKERS) { "scan hashing is limited to exactly two workers" }
        require(workerShutdownTimeoutMs > 0) { "worker shutdown timeout must be positive" }
    }

    private val executor: ExecutorService = Executors.newFixedThreadPool(workerCount)
    private val pending = ArrayDeque<Pending>()
    private var nextIndex = 0
    private var closed = false
    private var firstHashStartedAtNanos: Long? = null
    private var lastHashFinishedAtNanos: Long? = null

    val wallClockNanos: Long
        get() = if (firstHashStartedAtNanos == null || lastHashFinishedAtNanos == null) {
            0L
        } else {
            (lastHashFinishedAtNanos!! - firstHashStartedAtNanos!!).coerceAtLeast(0L)
        }

    fun start() {
        fillPending()
    }

    fun next(): Pair<DiscoveredAudioFile, Result>? {
        check(!closed) { "hash stage is closed" }
        fillPending()
        if (pending.isEmpty()) return null

        val item = pending.removeFirst()
        val result = await(item)
        fillPending()
        return item.file to result
    }

    override fun close() {
        if (closed) return
        closed = true
        pending.forEach { it.cancellation?.cancel() }
        executor.shutdownNow()
        try {
            executor.awaitTermination(workerShutdownTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        pending.clear()
    }

    fun hasLiveWorkers(): Boolean = !executor.isTerminated

    private fun fillPending() {
        while (!closed && pending.size < workerCount && nextIndex < files.size) {
            val file = files[nextIndex++]
            if (!shouldHash(file)) {
                pending.addLast(Pending(file, null, null))
                continue
            }
            val cancellation = WorkerHashCancellation(status)
            if (firstHashStartedAtNanos == null) firstHashStartedAtNanos = System.nanoTime()
            val future = executor.submit<FullFileHashResult> {
                try {
                    hash(file, cancellation)
                } finally {
                    lastHashFinishedAtNanos = System.nanoTime()
                }
            }
            pending.addLast(Pending(file, future, cancellation))
        }
    }

    private fun await(item: Pending): Result {
        val future = item.future ?: return Result.Skipped
        while (true) {
            try {
                return when (val hashResult = future.get(HASH_WAIT_POLL_MS, TimeUnit.MILLISECONDS)) {
                    FullFileHashResult.Cancelled -> Result.Cancelled
                    else -> Result.Hashed(hashResult)
                }
            } catch (_: TimeoutException) {
                if (status() != ScanSessionStatus.RUNNING) {
                    cancel(item)
                    cancelPending()
                    return Result.Cancelled
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                cancel(item)
                cancelPending()
                return Result.Cancelled
            } catch (_: ExecutionException) {
                return Result.Hashed(FullFileHashResult.ReadFailed)
            }
        }
    }

    private fun cancelPending() {
        pending.forEach {
            cancel(it)
        }
    }

    private fun cancel(item: Pending) {
        item.cancellation?.cancel()
        item.future?.cancel(true)
    }

    private data class Pending(
        val file: DiscoveredAudioFile,
        val future: Future<FullFileHashResult>?,
        val cancellation: WorkerHashCancellation?,
    )

    sealed interface Result {
        data class Hashed(val hashResult: FullFileHashResult) : Result
        data object Skipped : Result
        data object Cancelled : Result
    }

    private companion object {
        const val MAX_WORKERS = 2
        const val HASH_WAIT_POLL_MS = 100L
        const val WORKER_SHUTDOWN_TIMEOUT_MS = 1_000L
    }
}

/** Cancellation object that closes the currently active SAF stream before interrupting a worker. */
internal class WorkerHashCancellation(
    private val status: () -> ScanSessionStatus?,
) : HashCancellation {
    private val activeStream = AtomicReference<InputStream?>()
    @Volatile private var explicitlyCancelled = false
    @Volatile private var lastStatusCheckNanos = 0L
    @Volatile private var statusCancelled = false

    override fun isCancelled(): Boolean {
        if (explicitlyCancelled) return true
        val now = System.nanoTime()
        if (now - lastStatusCheckNanos >= STATUS_REFRESH_INTERVAL_NANOS) {
            lastStatusCheckNanos = now
            statusCancelled = status() != ScanSessionStatus.RUNNING
        }
        return statusCancelled
    }

    fun cancel() {
        explicitlyCancelled = true
        activeStream.getAndSet(null)?.closeQuietly()
    }

    override fun onStreamOpened(stream: InputStream) {
        if (explicitlyCancelled) {
            stream.closeQuietly()
        } else {
            activeStream.set(stream)
        }
    }

    override fun onStreamClosed(stream: InputStream) {
        activeStream.compareAndSet(stream, null)
    }

    private fun InputStream.closeQuietly() {
        runCatching { close() }
    }

    private companion object {
        const val STATUS_REFRESH_INTERVAL_NANOS = 100_000_000L
    }
}
