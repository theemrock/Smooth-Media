package com.theemrock.smoothmedia.artwork

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import com.theemrock.smoothmedia.library.LibraryArtworkCandidate
import com.theemrock.smoothmedia.library.LibraryArtworkCandidateRepository

sealed interface ArtworkRequest {
    val cacheKey: String

    data class Album(
        val album: String,
        val albumArtist: String,
    ) : ArtworkRequest {
        override val cacheKey: String = "album\u0000$album\u0000$albumArtist"
    }

    data class Track(val trackId: String) : ArtworkRequest {
        override val cacheKey: String = "track\u0000$trackId"
    }
}

class AndroidArtworkResolver(
    private val contentResolver: ContentResolver,
    private val candidates: LibraryArtworkCandidateRepository,
    maxCacheBytes: Int = 4 * 1024 * 1024,
) {
    private val bitmapCache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val failedCache = LruCache<String, Boolean>(128)

    fun load(request: ArtworkRequest, targetPx: Int): Bitmap? {
        bitmapCache.get(request.cacheKey)?.let { return it }
        if (failedCache.get(request.cacheKey) == true) return null
        val selector = EmbeddedArtworkSelector(
            candidateLoader = { key -> loadCandidates(request, key) },
            artworkReader = ::readEmbeddedArtwork,
        )
        val bytes = selector.load(request.cacheKey) ?: run {
            failedCache.put(request.cacheKey, true)
            return null
        }
        val bitmap = decodeSampled(bytes, targetPx) ?: run {
            failedCache.put(request.cacheKey, true)
            return null
        }
        bitmapCache.put(request.cacheKey, bitmap)
        return bitmap
    }

    private fun loadCandidates(request: ArtworkRequest, key: String): List<ArtworkCandidate> =
        when (request) {
            is ArtworkRequest.Album -> candidates.loadAlbumArtworkCandidates(request.album, request.albumArtist, 4)
            is ArtworkRequest.Track -> candidates.loadTrackArtworkCandidates(request.trackId, 2)
        }.map(LibraryArtworkCandidate::toArtworkCandidate)

    private fun readEmbeddedArtwork(locator: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            contentResolver.openFileDescriptor(Uri.parse(locator), "r")?.use { descriptor ->
                retriever.setDataSource(descriptor.fileDescriptor)
                retriever.embeddedPicture
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeSampled(bytes: ByteArray, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, targetPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun calculateSampleSize(width: Int, height: Int, targetPx: Int): Int {
        var sample = 1
        val target = targetPx.coerceAtLeast(1)
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
        return sample
    }
}

private fun LibraryArtworkCandidate.toArtworkCandidate(): ArtworkCandidate =
    ArtworkCandidate(trackId, locationId, locator)
