package com.theemrock.smoothmedia.catalog.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.catalog.database.entity.CatalogLifecycle
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.LocatorBindingEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackLocationEntity
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import com.theemrock.smoothmedia.catalog.repository.RoomLibrarySourceRepository
import com.theemrock.smoothmedia.catalog.repository.SourceRegistrationWriteResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrarySourceRepositoryTest {
    private val databaseName = "library-source-repository-test.db"
    private lateinit var context: Context
    private lateinit var database: CatalogDatabase
    private lateinit var repository: RoomLibrarySourceRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
        openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun registerSameActiveSource_isIdempotentAndPreservesCreation() {
        assertEquals(SourceRegistrationWriteResult.Added, repository.registerOrReactivate(source()))

        val duplicate = source(
            persistedPermissionFlags = 1,
            createdAtEpochMs = 9_000,
            updatedAtEpochMs = 2_000,
        )
        assertEquals(SourceRegistrationWriteResult.AlreadyActive, repository.registerOrReactivate(duplicate))

        val stored = repository.find("source-1")!!
        assertEquals(1_000, stored.createdAtEpochMs)
        assertEquals(2_000, stored.updatedAtEpochMs)
        assertEquals(1, stored.persistedPermissionFlags)
        assertEquals(listOf("source-1"), repository.listAll().map { it.sourceId })
    }

    @Test
    fun removedSource_reactivatesWithSameIdentityAndCatalogRelations() {
        repository.registerOrReactivate(source())
        insertCatalogRelations()

        assertTrue(repository.markRemoved("source-1", 2_000))
        assertEquals(SourceSelectionState.REMOVED, repository.find("source-1")!!.selectionState)
        assertCatalogRelationsRemain()

        val result = repository.registerOrReactivate(source(createdAtEpochMs = 9_000, updatedAtEpochMs = 3_000))

        assertEquals(SourceRegistrationWriteResult.Reactivated, result)
        val stored = repository.find("source-1")!!
        assertEquals(SourceSelectionState.ACTIVE, stored.selectionState)
        assertEquals(1_000, stored.createdAtEpochMs)
        assertCatalogRelationsRemain()
    }

    @Test
    fun accessStateAndDatabaseReopen_preserveSourceRecord() {
        repository.registerOrReactivate(source())
        assertTrue(
            repository.updateAccessState(
                sourceId = "source-1",
                persistedPermissionFlags = 0,
                permissionState = SourcePermissionState.LOST,
                availability = SourceAvailability.PERMISSION_LOST,
                updatedAtEpochMs = 2_000,
            ),
        )

        database.close()
        openDatabase()

        val stored = repository.findByRootUri("content://provider/tree/primary%3AMusic")!!
        assertEquals("source-1", stored.sourceId)
        assertEquals(SourcePermissionState.LOST, stored.permissionState)
        assertEquals(SourceAvailability.PERMISSION_LOST, stored.availability)
        assertEquals(SourceSelectionState.ACTIVE, stored.selectionState)
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, CatalogDatabase::class.java, databaseName)
            .addCallback(CatalogDatabaseCallback)
            .addMigrations(*CatalogMigrations.ALL)
            .build()
        repository = RoomLibrarySourceRepository(database)
    }

    private fun insertCatalogRelations() {
        database.trackDao().insertTrack(
            TrackEntity("track-1", CatalogLifecycle.ACTIVE, 1_000, 1_000),
        )
        val hash = StoredFileHash("SHA-256", 1, "digest")
        database.trackDao().insertLocation(
            TrackLocationEntity(
                locationId = "location-1",
                trackId = "track-1",
                sourceId = "source-1",
                originalFileHash = hash,
                currentFileHash = hash,
                format = AudioFormat.FLAC,
                availability = LocationAvailability.AVAILABLE,
                path = null,
                mediaStoreId = null,
                fileSizeBytes = 42,
                modifiedAtEpochMs = 900,
                durationMs = 180_000,
                bitrate = 900_000,
                sampleRateHz = 96_000,
                channelCount = 2,
                createdAtEpochMs = 1_000,
                updatedAtEpochMs = 1_000,
            ),
        )
        database.locatorBindingDao().insert(
            LocatorBindingEntity("content://provider/document/track", "location-1", "source-1", 1_000),
        )
    }

    private fun assertCatalogRelationsRemain() {
        assertNotNull(database.trackDao().findTrack("track-1"))
        assertNotNull(database.trackDao().findLocation("location-1"))
        assertNotNull(database.locatorBindingDao().findByLocation("location-1"))
    }

    private fun source(
        persistedPermissionFlags: Int = 1,
        createdAtEpochMs: Long = 1_000,
        updatedAtEpochMs: Long = 1_000,
    ) = LibrarySourceEntity(
        sourceId = "source-1",
        sourceType = LibrarySourceType.SAF_TREE,
        rootUri = "content://provider/tree/primary%3AMusic",
        volumeName = null,
        persistedPermissionFlags = persistedPermissionFlags,
        permissionState = SourcePermissionState.GRANTED,
        availability = SourceAvailability.AVAILABLE,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        selectionState = SourceSelectionState.ACTIVE,
    )
}
