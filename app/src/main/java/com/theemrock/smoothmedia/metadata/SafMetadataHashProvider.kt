package com.theemrock.smoothmedia.metadata

import android.content.ContentResolver
import android.net.Uri
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash
import com.theemrock.smoothmedia.discovery.FullFileHashResult
import com.theemrock.smoothmedia.discovery.StreamingSha256Hasher

class SafMetadataHashProvider(
    private val contentResolver: ContentResolver,
    private val hasher: StreamingSha256Hasher = StreamingSha256Hasher(),
) : (String) -> StoredFileHash? {
    override fun invoke(locator: String): StoredFileHash? {
        val uri = runCatching { Uri.parse(locator) }.getOrNull() ?: return null
        val input = runCatching { contentResolver.openInputStream(uri) }.getOrNull() ?: return null
        return input.use { stream ->
            when (val result = hasher.hash(stream)) {
                is FullFileHashResult.Success -> StoredFileHash(
                    algorithmName = result.algorithmName,
                    algorithmVersion = result.algorithmVersion,
                    digest = result.digest,
                )
                else -> null
            }
        }
    }
}
