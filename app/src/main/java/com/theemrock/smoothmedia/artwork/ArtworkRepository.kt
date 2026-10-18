package com.theemrock.smoothmedia.artwork

import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.ArtworkAlbumBindingEntity
import com.theemrock.smoothmedia.catalog.database.entity.ArtworkAssetEntity
import com.theemrock.smoothmedia.catalog.database.entity.ArtworkOverrideEntity

/** Persistence boundary for artwork discovery and explicit user overrides. */
class ArtworkRepository(private val database: CatalogDatabase) {
    fun saveDiscoveredAsset(asset: ArtworkAssetEntity, binding: ArtworkAlbumBindingEntity): Boolean {
        return database.runInTransaction<Boolean> {
            if (database.artworkDao().findOverride(binding.collectionKey) != null) return@runInTransaction false
            database.artworkDao().upsertAsset(asset)
            database.artworkDao().upsertBinding(binding)
            true
        }
    }

    fun saveUserOverride(asset: ArtworkAssetEntity, override: ArtworkOverrideEntity) {
        database.runInTransaction {
            database.artworkDao().upsertAsset(asset)
            database.artworkDao().upsertOverride(override)
        }
    }

    fun resolveAsset(collectionKey: String): ArtworkAssetEntity? {
        val dao = database.artworkDao()
        val assetId = dao.findOverride(collectionKey)?.assetId ?: dao.findBinding(collectionKey)?.assetId
        return assetId?.let(dao::findAsset)?.takeIf { it.availability == "AVAILABLE" }
    }
}
