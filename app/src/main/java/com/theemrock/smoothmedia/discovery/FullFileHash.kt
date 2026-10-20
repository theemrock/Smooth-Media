package com.theemrock.smoothmedia.discovery

import android.content.ContentResolver
import android.net.Uri
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** Cancellation boundary for a hash operation; it does not mutate the source. */
fun interface HashCancellation {
    fun isCancelled(): Boolean

    /** Optional lifecycle hooks used by bounded worker cancellation to close an active stream. */
    fun onStreamOpened(stream: InputStream) = Unit
    fun onStreamClosed(stream: InputStream) = Unit
}

sealed interface FullFileHashResult {
    data class Success(
        val algorithmName: String,
        val algorithmVersion: Int,
        val digest: String,
        val bytesRead: Long,
    ) : FullFileHashResult

    data object Cancelled : FullFileHashResult

    data object PermissionLost : FullFileHashResult

    data object Unreadable : FullFileHashResult

    data object ReadFailed : FullFileHashResult
}

/** Computes a SHA-256 digest with a bounded read buffer and no Room dependency. */
class StreamingSha256Hasher(
    private val chunkSizeBytes: Int = DEFAULT_CHUNK_SIZE_BYTES,
) {
    init {
        require(chunkSizeBytes > 0) { "chunkSizeBytes must be positive" }
    }

    fun hash(
        input: InputStream,
        cancellation: HashCancellation = HashCancellation { false },
    ): FullFileHashResult {
        if (cancellation.isCancelled()) return FullFileHashResult.Cancelled

        val digest = MessageDigest.getInstance(ALGORITHM_NAME)
        val buffer = ByteArray(chunkSizeBytes)
        var bytesRead = 0L

        return try {
            input.use { stream ->
                while (true) {
                    if (cancellation.isCancelled()) return FullFileHashResult.Cancelled
                    val count = stream.read(buffer, 0, buffer.size)
                    if (count < 0) break
                    if (count == 0) continue
                    digest.update(buffer, 0, count)
                    bytesRead += count
                }
            }
            if (cancellation.isCancelled()) {
                FullFileHashResult.Cancelled
            } else {
                FullFileHashResult.Success(
                    algorithmName = ALGORITHM_NAME,
                    algorithmVersion = ALGORITHM_VERSION,
                    digest = digest.digest().toHex(),
                    bytesRead = bytesRead,
                )
            }
        } catch (_: SecurityException) {
            FullFileHashResult.PermissionLost
        } catch (_: CancellationException) {
            FullFileHashResult.Cancelled
        } catch (_: IOException) {
            FullFileHashResult.ReadFailed
        } catch (_: Exception) {
            FullFileHashResult.ReadFailed
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    private companion object {
        const val ALGORITHM_NAME = "SHA-256"
        const val ALGORITHM_VERSION = 1
        const val DEFAULT_CHUNK_SIZE_BYTES = 64 * 1024
    }
}

/** Hashes one discovery result through the already-granted SAF ContentResolver stream. */
class SafFullFileHasher(
    private val contentResolver: ContentResolver,
    private val streamingHasher: StreamingSha256Hasher = StreamingSha256Hasher(),
    private val onStreamOpenDurationNanos: (Long) -> Unit = {},
) {
    fun hash(
        file: DiscoveredAudioFile,
        cancellation: HashCancellation = HashCancellation { false },
    ): FullFileHashResult {
        if (cancellation.isCancelled()) return FullFileHashResult.Cancelled
        if (file.uri.isBlank()) return FullFileHashResult.Unreadable

        val uri = try {
            Uri.parse(file.uri)
        } catch (_: IllegalArgumentException) {
            return FullFileHashResult.Unreadable
        }

        return try {
            val openStartedAt = System.nanoTime()
            val input = contentResolver.openInputStream(uri)
                .also { onStreamOpenDurationNanos(System.nanoTime() - openStartedAt) }
                ?: return FullFileHashResult.Unreadable
            cancellation.onStreamOpened(input)
            try {
                streamingHasher.hash(input, cancellation)
            } finally {
                cancellation.onStreamClosed(input)
            }
        } catch (_: SecurityException) {
            FullFileHashResult.PermissionLost
        } catch (_: FileNotFoundException) {
            FullFileHashResult.Unreadable
        } catch (_: IOException) {
            FullFileHashResult.ReadFailed
        } catch (_: IllegalArgumentException) {
            FullFileHashResult.Unreadable
        }
    }
}
