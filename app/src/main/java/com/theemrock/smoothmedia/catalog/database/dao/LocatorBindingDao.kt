package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theemrock.smoothmedia.catalog.database.entity.LocatorBindingEntity

@Dao
interface LocatorBindingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(binding: LocatorBindingEntity)

    @Query("SELECT * FROM locator_bindings WHERE locator = :locator")
    fun findByLocator(locator: String): LocatorBindingEntity?

    @Query("SELECT * FROM locator_bindings WHERE location_id = :locationId")
    fun findByLocation(locationId: String): LocatorBindingEntity?

    @Query("SELECT * FROM locator_bindings ORDER BY locator")
    fun listAll(): List<LocatorBindingEntity>
}
