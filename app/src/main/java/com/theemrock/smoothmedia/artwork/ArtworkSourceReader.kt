package com.theemrock.smoothmedia.artwork

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.theemrock.smoothmedia.discovery.AndroidSafDocumentTreeReader
import com.theemrock.smoothmedia.discovery.DirectoryReadResult
import java.io.ByteArrayOutputStream
import java.io.InputStream

enum class ArtworkSourceType {
    EMBEDDED_AUDIO,
    FOLDER_IMAGE,
}

enum class ArtworkAvailability {
    AVAILABLE,
    MISSING,
    UNAVAILABLE,
    FAILED,
}

enum class ArtworkFailureReason {
    PERMISSION_LOST,
    SOURCE_UNAVAILABLE,
    CORRUPT_IMAGE,
    TOO_LARGE,
    CANCELLED,
    READ_FAILED,
}

data class ArtworkImageInfo(
    val format: String,
    val width: Int,
    val height: Int,
)

data class ArtworkFileCandidate(
    val displayName: String,
    val locator: String,
)

data class ArtworkSourceResult(
    val sourceType: ArtworkSourceType,
    val locator: String?,
    val availability: ArtworkAvailability,
    val format: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val failureReason: ArtworkFailureReason? = null,
    internal val payload: ByteArray? = null,
)

fun interface ArtworkImageInspector {
    fun inspect(bytes: ByteArray): ArtworkImageInfo?
}

interface ArtworkSourceBackend {
    fun readEmbedded(locator: String, maxEncodedBytes: Int): ByteArray?

    fun listFolder(folderLocator: String): List<ArtworkFileCandidate>

    fun readFile(locator: String, maxEncodedBytes: Int): ByteArray?
}

/** Reads artwork candidates without changing Room, SAF grants, or source files. */
class ArtworkSourceReader(
    private val backend: ArtworkSourceBackend,
    private val inspector: ArtworkImageInspector,
    private val maxEncodedBytes: Int = DEFAULT_MAX_ENCODED_BYTES,
) {
    fun readEmbedded(locator: String): ArtworkSourceResult {
        return try {
            val payload = backend.readEmbedded(locator, maxEncodedBytes)
                ?: return missing(ArtworkSourceType.EMBEDDED_AUDIO, locator)
            inspect(ArtworkSourceType.EMBEDDED_AUDIO, locator, payload)
        } catch (_: SecurityException) {
            failure(ArtworkSourceType.EMBEDDED_AUDIO, locator, ArtworkFailureReason.PERMISSION_LOST)
        } catch (_: ArtworkPayloadTooLargeException) {
            failure(ArtworkSourceType.EMBEDDED_AUDIO, locator, ArtworkFailureReason.TOO_LARGE)
        } catch (_: IllegalArgumentException) {
            failure(ArtworkSourceType.EMBEDDED_AUDIO, locator, ArtworkFailureReason.SOURCE_UNAVAILABLE)
        } catch (_: Exception) {
            failure(ArtworkSourceType.EMBEDDED_AUDIO, locator, ArtworkFailureReason.READ_FAILED)
        }
    }

    fun readFolderArtwork(
        folderLocator: String,
        isCancelled: () -> Boolean = { false },
    ): ArtworkSourceResult {
        if (isCancelled()) return failure(ArtworkSourceType.FOLDER_IMAGE, folderLocator, ArtworkFailureReason.CANCELLED)
        val children = try {
            backend.listFolder(folderLocator)
        } catch (_: SecurityException) {
            return failure(ArtworkSourceType.FOLDER_IMAGE, folderLocator, ArtworkFailureReason.PERMISSION_LOST)
        } catch (_: IllegalArgumentException) {
            return failure(ArtworkSourceType.FOLDER_IMAGE, folderLocator, ArtworkFailureReason.SOURCE_UNAVAILABLE)
        } catch (_: Exception) {
            return failure(ArtworkSourceType.FOLDER_IMAGE, folderLocator, ArtworkFailureReason.READ_FAILED)
        }
        val candidates = children
            .filter { it.displayName.equals("cover.jpg", ignoreCase = true) || it.displayName.equals("folder.jpg", ignoreCase = true) }
            .sortedWith(compareBy<ArtworkFileCandidate> { if (it.displayName.equals("cover.jpg", true)) 0 else 1 }.thenBy { it.displayName })
            .distinctBy { it.locator }
        if (candidates.isEmpty()) return missing(ArtworkSourceType.FOLDER_IMAGE, folderLocator)

        var lastFailure: ArtworkFailureReason? = null
        var foundPayload = false
        for (candidate in candidates) {
            if (isCancelled()) return failure(ArtworkSourceType.FOLDER_IMAGE, candidate.locator, ArtworkFailureReason.CANCELLED)
            try {
                val payload = backend.readFile(candidate.locator, maxEncodedBytes)
                if (payload == null) continue
                foundPayload = true
                val result = inspect(ArtworkSourceType.FOLDER_IMAGE, candidate.locator, payload)
                if (result.availability == ArtworkAvailability.AVAILABLE) return result
                lastFailure = result.failureReason
            } catch (_: SecurityException) {
                lastFailure = ArtworkFailureReason.PERMISSION_LOST
            } catch (_: ArtworkPayloadTooLargeException) {
                lastFailure = ArtworkFailureReason.TOO_LARGE
            } catch (_: Exception) {
                lastFailure = ArtworkFailureReason.READ_FAILED
            }
        }
        if (!foundPayload) return missing(ArtworkSourceType.FOLDER_IMAGE, candidates.first().locator)
        return failure(ArtworkSourceType.FOLDER_IMAGE, candidates.first().locator, lastFailure ?: ArtworkFailureReason.CORRUPT_IMAGE)
    }

    private fun inspect(type: ArtworkSourceType, locator: String, payload: ByteArray): ArtworkSourceResult {
        if (payload.size > maxEncodedBytes) return failure(type, locator, ArtworkFailureReason.TOO_LARGE)
        val info = inspector.inspect(payload)
            ?: return failure(type, locator, ArtworkFailureReason.CORRUPT_IMAGE)
        return ArtworkSourceResult(type, locator, ArtworkAvailability.AVAILABLE, info.format, info.width, info.height, payload = payload)
    }

    private fun missing(type: ArtworkSourceType, locator: String) =
        ArtworkSourceResult(type, locator, ArtworkAvailability.MISSING)

    private fun failure(type: ArtworkSourceType, locator: String?, reason: ArtworkFailureReason) =
        ArtworkSourceResult(type, locator, ArtworkAvailability.FAILED, failureReason = reason)

    companion object {
        const val DEFAULT_MAX_ENCODED_BYTES: Int = 8 * 1024 * 1024
    }
}

internal class ArtworkPayloadTooLargeException : RuntimeException()

/** Android adapter for the existing ContentResolver and SAF tree reader. */
class AndroidArtworkSourceBackend(
    private val contentResolver: ContentResolver,
    private val treeReader: AndroidSafDocumentTreeReader = AndroidSafDocumentTreeReader(contentResolver),
) : ArtworkSourceBackend {
    override fun readEmbedded(locator: String, maxEncodedBytes: Int): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            contentResolver.openFileDescriptor(Uri.parse(locator), "r")?.use { descriptor ->
                retriever.setDataSource(descriptor.fileDescriptor)
                retriever.embeddedPicture?.also { if (it.size > maxEncodedBytes) throw ArtworkPayloadTooLargeException() }
            }
        } finally {
            runCatching { retriever.release() }
        }
    }

    override fun listFolder(folderLocator: String): List<ArtworkFileCandidate> = when (val result = treeReader.listChildren(folderLocator)) {
        is DirectoryReadResult.Readable -> result.children
            .filterNot { it.isDirectory }
            .map { ArtworkFileCandidate(it.displayName, it.uri) }
        DirectoryReadResult.PermissionLost -> throw SecurityException("SAF permission lost")
        DirectoryReadResult.Unavailable -> throw IllegalArgumentException("SAF source unavailable")
        DirectoryReadResult.Unreadable -> throw IllegalArgumentException("SAF folder unreadable")
        DirectoryReadResult.Failed -> throw IllegalArgumentException("SAF folder read failed")
    }

    override fun readFile(locator: String, maxEncodedBytes: Int): ByteArray? =
        contentResolver.openInputStream(Uri.parse(locator))?.use { it.readBounded(maxEncodedBytes) }
}

class AndroidBitmapArtworkInspector : ArtworkImageInspector {
    override fun inspect(bytes: ByteArray): ArtworkImageInfo? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val format = bounds.outMimeType ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return ArtworkImageInfo(format, bounds.outWidth, bounds.outHeight)
    }
}

private fun InputStream.readBounded(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw ArtworkPayloadTooLargeException()
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
