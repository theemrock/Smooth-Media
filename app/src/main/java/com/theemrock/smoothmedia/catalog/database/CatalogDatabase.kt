package com.theemrock.smoothmedia.catalog.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.theemrock.smoothmedia.catalog.database.dao.LibrarySourceDao
import com.theemrock.smoothmedia.catalog.database.dao.LocatorBindingDao
import com.theemrock.smoothmedia.catalog.database.dao.CatalogExclusionDao
import com.theemrock.smoothmedia.catalog.database.dao.ScanDao
import com.theemrock.smoothmedia.catalog.database.dao.TrackDao
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.ActiveScanLeaseEntity
import com.theemrock.smoothmedia.catalog.database.entity.CatalogExclusionEntity
import com.theemrock.smoothmedia.catalog.database.entity.LocatorBindingEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceScanCheckpointEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackLocationEntity

private const val CATALOG_SCHEMA_VERSION = 1

@Database(
    entities = [
        TrackEntity::class,
        LibrarySourceEntity::class,
        TrackLocationEntity::class,
        LocatorBindingEntity::class,
        ScanSessionEntity::class,
        ActiveScanLeaseEntity::class,
        SourceScanCheckpointEntity::class,
        ScanCandidateEntity::class,
        CatalogExclusionEntity::class,
    ],
    version = CATALOG_SCHEMA_VERSION,
    exportSchema = true,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    abstract fun librarySourceDao(): LibrarySourceDao

    abstract fun locatorBindingDao(): LocatorBindingDao

    abstract fun scanDao(): ScanDao

    abstract fun catalogExclusionDao(): CatalogExclusionDao

    companion object {
        const val SCHEMA_VERSION = CATALOG_SCHEMA_VERSION
    }
}
