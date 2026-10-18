package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theemrock.smoothmedia.catalog.database.entity.ArtworkAlbumBindingEntity
import com.theemrock.smoothmedia.catalog.database.entity.ArtworkAssetEntity
import com.theemrock.smoothmedia.catalog.database.entity.ArtworkOverrideEntity

@Dao
interface ArtworkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAsset(asset: ArtworkAssetEntity)

    @Query("SELECT * FROM artwork_assets WHERE assetId = :assetId")
    fun findAsset(assetId: String): ArtworkAssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBinding(binding: ArtworkAlbumBindingEntity)

    @Query("SELECT * FROM artwork_album_bindings WHERE collectionKey = :collectionKey")
    fun findBinding(collectionKey: String): ArtworkAlbumBindingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertOverride(override: ArtworkOverrideEntity)

    @Query("SELECT * FROM artwork_overrides WHERE collectionKey = :collectionKey")
    fun findOverride(collectionKey: String): ArtworkOverrideEntity?
}
