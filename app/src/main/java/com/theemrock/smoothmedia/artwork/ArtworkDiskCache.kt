package com.theemrock.smoothmedia.artwork

import java.io.File
import java.security.MessageDigest

object ArtworkCacheKey {
    fun targetBucket(targetPx: Int): Int {
        val target = targetPx.coerceAtLeast(1)
        return listOf(96, 128, 192, 720).firstOrNull { it >= target } ?: target
    }

    fun forThumbnail(assetKey: String, targetPx: Int, sourceVersion: String = "v1"): String =
        "$assetKey|$targetPx|$sourceVersion"
}

/** Small app-private encoded-artwork cache. It never stores catalog or override data. */
class ArtworkDiskCache(
    private val directory: File,
    private val maxBytes: Long = 32L * 1024L * 1024L,
) {
    init {
        require(maxBytes > 0) { "maxBytes must be positive" }
    }

    @Synchronized
    fun get(key: String): ByteArray? {
        val file = fileFor(key)
        if (!file.isFile) return null
        val stored = runCatching { file.readBytes() }.getOrNull() ?: return removeAndNull(file)
        val separator = stored.indexOf(NEWLINE)
        if (separator != HASH_HEX_LENGTH || stored.size <= separator + 1) return removeAndNull(file)
        val expectedHash = stored.copyOfRange(0, separator).decodeToString()
        val payload = stored.copyOfRange(separator + 1, stored.size)
        if (expectedHash != sha256(payload)) return removeAndNull(file)
        file.setLastModified(System.currentTimeMillis())
        return payload
    }

    @Synchronized
    fun put(key: String, payload: ByteArray) {
        if (payload.size.toLong() > maxBytes) return
        directory.mkdirs()
        val target = fileFor(key)
        val temporary = File(directory, ".${target.name}.tmp-${System.nanoTime()}")
        runCatching {
            temporary.writeBytes(sha256(payload).encodeToByteArray() + byteArrayOf(NEWLINE) + payload)
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "artwork cache rename failed" }
            evictIfNeeded()
        }.onFailure { temporary.delete() }
    }

    @Synchronized
    fun invalidate(key: String) {
        fileFor(key).delete()
    }

    @Synchronized
    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun fileFor(key: String): File = File(directory, "${sha256(key.encodeToByteArray())}.bin")

    private fun evictIfNeeded() {
        var total = directory.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".tmp") }.sumOf { it.length() }
        if (total <= maxBytes) return
        directory.listFiles().orEmpty()
            .filter { it.isFile && !it.name.endsWith(".tmp") }
            .sortedBy { it.lastModified() }
            .forEach { file ->
                if (total <= maxBytes) return@forEach
                val length = file.length()
                if (file.delete()) total -= length
            }
    }

    private fun removeAndNull(file: File): ByteArray? {
        file.delete()
        return null
    }

    private fun sha256(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }

    private companion object {
        const val HASH_HEX_LENGTH = 64
        const val NEWLINE: Byte = 10
    }
}
