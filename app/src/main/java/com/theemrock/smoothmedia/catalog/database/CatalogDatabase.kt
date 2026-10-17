package com.theemrock.smoothmedia.catalog.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.theemrock.smoothmedia.catalog.database.dao.LibrarySourceDao
import com.theemrock.smoothmedia.catalog.database.dao.LibraryDao
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
import com.theemrock.smoothmedia.catalog.database.entity.TrackMetadataEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackMetadataOverrideEntity
import com.theemrock.smoothmedia.catalog.database.entity.LyricsCacheEntity
import com.theemrock.smoothmedia.catalog.database.entity.SafDocumentEntity
import com.theemrock.smoothmedia.catalog.database.entity.GenreNodeEntity
import com.theemrock.smoothmedia.catalog.database.entity.GenreEdgeEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackGenreAssignmentEntity

private const val CATALOG_SCHEMA_VERSION = 7

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
        TrackMetadataEntity::class,
        TrackMetadataOverrideEntity::class,
        LyricsCacheEntity::class,
        SafDocumentEntity::class,
        GenreNodeEntity::class,
        GenreEdgeEntity::class,
        TrackGenreAssignmentEntity::class,
    ],
    version = CATALOG_SCHEMA_VERSION,
    exportSchema = true,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    abstract fun librarySourceDao(): LibrarySourceDao

    abstract fun libraryDao(): LibraryDao

    abstract fun locatorBindingDao(): LocatorBindingDao

    abstract fun scanDao(): ScanDao

    abstract fun catalogExclusionDao(): CatalogExclusionDao

    abstract fun trackMetadataDao(): com.theemrock.smoothmedia.catalog.database.dao.TrackMetadataDao

    abstract fun lyricsCacheDao(): com.theemrock.smoothmedia.catalog.database.dao.LyricsCacheDao

    abstract fun safDocumentDao(): com.theemrock.smoothmedia.catalog.database.dao.SafDocumentDao

    abstract fun genreDao(): com.theemrock.smoothmedia.catalog.database.dao.GenreDao

    companion object {
        const val SCHEMA_VERSION = CATALOG_SCHEMA_VERSION
    }
}
