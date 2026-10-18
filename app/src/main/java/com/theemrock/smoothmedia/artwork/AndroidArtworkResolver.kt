package com.theemrock.smoothmedia.artwork

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.theemrock.smoothmedia.library.LibraryArtworkCandidate
import com.theemrock.smoothmedia.library.LibraryArtworkCandidateRepository

sealed interface ArtworkRequest {
    val cacheKey: String

    data class Album(
        val album: String,
        val albumArtist: String,
        val collectionKey: String? = null,
    ) : ArtworkRequest {
        override val cacheKey: String = collectionKey?.let { "album-key\u0000$it" }
            ?: "album\u0000$album\u0000$albumArtist"
    }

    data class Track(val trackId: String) : ArtworkRequest {
        override val cacheKey: String = "track\u0000$trackId"
    }
}

class AndroidArtworkResolver(
    private val contentResolver: ContentResolver,
    private val candidates: LibraryArtworkCandidateRepository,
    maxCacheBytes: Int = 4 * 1024 * 1024,
    private val diskCache: ArtworkDiskCache? = null,
    private val artworkRepository: ArtworkRepository? = null,
) {
    private val sourceReader = ArtworkSourceReader(
        backend = AndroidArtworkSourceBackend(contentResolver),
        inspector = AndroidBitmapArtworkInspector(),
    )
    private val bitmapCache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val failedCache = LruCache<String, Boolean>(128)

    fun load(request: ArtworkRequest, targetPx: Int): Bitmap? {
        val persistedAsset = persistedAsset(request)
        val cacheIdentity = persistedAsset?.assetId ?: request.cacheKey
        val cacheVersion = persistedAsset?.updatedAtEpochMs?.toString() ?: "v1"
        val cacheKey = ArtworkCacheKey.forThumbnail(cacheIdentity, targetPx, cacheVersion)
        bitmapCache.get(cacheKey)?.let { return it }
        diskCache?.get(cacheKey)?.let { encoded ->
            decodeSampled(encoded, targetPx)?.let { bitmap ->
                bitmapCache.put(cacheKey, bitmap)
                return bitmap
            }
            diskCache.invalidate(cacheKey)
        }
        if (failedCache.get(cacheKey) == true) return null
        val selector = EmbeddedArtworkSelector(
            candidateLoader = { key -> loadCandidates(request, key) },
            artworkReader = ::readEmbeddedArtwork,
        )
        val bytes = persistedAsset?.let(::readPersistedArtwork)
            ?: selector.load(request.cacheKey)
            ?: run {
            failedCache.put(cacheKey, true)
            return null
        }
        val bitmap = decodeSampled(bytes, targetPx) ?: run {
            failedCache.put(cacheKey, true)
            return null
        }
        diskCache?.put(cacheKey, bytes)
        bitmapCache.put(cacheKey, bitmap)
        return bitmap
    }

    fun invalidate(request: ArtworkRequest, targetPx: Int) {
        val cacheKey = ArtworkCacheKey.forThumbnail(request.cacheKey, targetPx)
        bitmapCache.remove(cacheKey)
        failedCache.remove(cacheKey)
        diskCache?.invalidate(cacheKey)
    }

    private fun loadCandidates(request: ArtworkRequest, key: String): List<ArtworkCandidate> =
        when (request) {
            is ArtworkRequest.Album -> candidates.loadAlbumArtworkCandidates(request.album, request.albumArtist, 4)
            is ArtworkRequest.Track -> candidates.loadTrackArtworkCandidates(request.trackId, 2)
        }.map(LibraryArtworkCandidate::toArtworkCandidate)

    private fun readEmbeddedArtwork(locator: String): ByteArray? {
        return sourceReader.readEmbedded(locator).payload
    }

    private fun persistedAsset(request: ArtworkRequest) =
        (request as? ArtworkRequest.Album)
            ?.collectionKey
            ?.let { artworkRepository?.resolveAsset(it) }

    private fun readPersistedArtwork(asset: com.theemrock.smoothmedia.catalog.database.entity.ArtworkAssetEntity): ByteArray? =
        when (asset.sourceType) {
            ArtworkSourceType.EMBEDDED_AUDIO.name -> sourceReader.readEmbedded(asset.sourceLocator).payload
            ArtworkSourceType.FOLDER_IMAGE.name -> sourceReader.readFolderFile(asset.sourceLocator).payload
            else -> null
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
