package com.theemrock.smoothmedia.metadata

import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash

/** Commits a verified file write and its new catalog metadata as one Room transaction. */
class MetadataFileWriteReconciler(
    private val database: CatalogDatabase,
    private val normalizer: MetadataNormalizer = MetadataNormalizer(),
) {
    fun reconcile(
        target: MetadataFileWriteTarget,
        raw: RawAudioMetadata,
        newHash: StoredFileHash,
        clearOverrides: Set<MetadataOverrideField> = emptySet(),
        nowEpochMs: Long,
    ): Boolean {
        val normalized = normalizer.normalize(raw)
        return runCatching {
            database.runInTransaction {
                val updated = database.trackDao().updateCurrentHashIfExpected(
                    locationId = target.locationId,
                    expectedAlgorithmName = target.currentHash.algorithmName,
                    expectedAlgorithmVersion = target.currentHash.algorithmVersion,
                    expectedDigest = target.currentHash.digest,
                    replacementAlgorithmName = newHash.algorithmName,
                    replacementAlgorithmVersion = newHash.algorithmVersion,
                    replacementDigest = newHash.digest,
                    updatedAtEpochMs = nowEpochMs,
                )
                check(updated == 1) { "Current hash changed before Room reconciliation" }
                RoomTrackMetadataRepository(database.trackMetadataDao()).replaceFileMetadata(
                    trackId = target.trackId,
                    raw = raw,
                    normalized = normalized,
                    updatedAtEpochMs = nowEpochMs,
                )
                clearOverrides.forEach { field ->
                    database.trackMetadataDao().clearOverride(target.trackId, field.name)
                }
            }
            true
        }.getOrDefault(false)
    }
}
