package com.theemrock.smoothmedia.artwork

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkDiskCacheTest {
    @Test
    fun targetBucketNeverUsesSmallerDecodeForLargerRequest() {
        assertEquals(96, ArtworkCacheKey.targetBucket(64))
        assertEquals(128, ArtworkCacheKey.targetBucket(100))
        assertEquals(192, ArtworkCacheKey.targetBucket(192))
        assertEquals(1000, ArtworkCacheKey.targetBucket(1000))
    }

    @Test
    fun cacheStoresAndReturnsEncodedArtwork() {
        val root = Files.createTempDirectory("artwork-cache-").toFile()
        try {
            val cache = ArtworkDiskCache(root, maxBytes = 1024)
            val key = ArtworkCacheKey.forThumbnail("asset-a", 192)
            cache.put(key, byteArrayOf(1, 2, 3))

            assertEquals(listOf<Byte>(1, 2, 3), cache.get(key)?.toList())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun invalidationRemovesOnlyRequestedArtwork() {
        val root = Files.createTempDirectory("artwork-cache-").toFile()
        try {
            val cache = ArtworkDiskCache(root, maxBytes = 1024)
            val first = ArtworkCacheKey.forThumbnail("asset-a", 128)
            val second = ArtworkCacheKey.forThumbnail("asset-b", 128)
            cache.put(first, byteArrayOf(1))
            cache.put(second, byteArrayOf(2))

            cache.invalidate(first)

            assertNull(cache.get(first))
            assertEquals(listOf<Byte>(2), cache.get(second)?.toList())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun capacityEvictsOldestEntriesWithoutAffectingOtherKeys() {
        val root = Files.createTempDirectory("artwork-cache-").toFile()
        try {
            val cache = ArtworkDiskCache(root, maxBytes = 100)
            val first = ArtworkCacheKey.forThumbnail("asset-a", 128)
            val second = ArtworkCacheKey.forThumbnail("asset-b", 128)
            cache.put(first, ByteArray(8) { 1 })
            cache.put(second, ByteArray(8) { 2 })

            assertNull(cache.get(first))
            assertEquals(8L, cache.get(second)?.size?.toLong())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun corruptedEntryIsIgnoredAndRemoved() {
        val root = Files.createTempDirectory("artwork-cache-").toFile()
        try {
            val cache = ArtworkDiskCache(root, maxBytes = 1024)
            val key = ArtworkCacheKey.forThumbnail("asset-a", 128)
            cache.put(key, byteArrayOf(1, 2, 3))
            root.listFiles()!!.first().writeBytes("corrupt".toByteArray())

            assertNull(cache.get(key))
            assertTrue(root.listFiles().isNullOrEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun profileAndSourceVersionProduceDifferentKeys() {
        assertTrue(
            ArtworkCacheKey.forThumbnail("asset-a", 128, "v1") !=
                ArtworkCacheKey.forThumbnail("asset-a", 192, "v1"),
        )
        assertTrue(
            ArtworkCacheKey.forThumbnail("asset-a", 128, "v1") !=
                ArtworkCacheKey.forThumbnail("asset-a", 128, "v2"),
        )
    }
}
