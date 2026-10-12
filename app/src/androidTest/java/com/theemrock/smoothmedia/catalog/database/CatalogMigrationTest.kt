package com.theemrock.smoothmedia.catalog.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogMigrationTest {
    private val databaseName = "catalog-migration-test.db"
    private lateinit var context: Context

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CatalogDatabase::class.java,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun exportedVersionOneSchema_opensWithoutDestructiveFallback() {
        assertEquals(1, CatalogDatabase.SCHEMA_VERSION)
        migrationHelper.createDatabase(databaseName, CatalogDatabase.SCHEMA_VERSION).close()

        val database = Room.databaseBuilder(context, CatalogDatabase::class.java, databaseName)
            .addCallback(CatalogDatabaseCallback)
            .addMigrations(*CatalogMigrations.ALL)
            .build()
        try {
            database.openHelper.writableDatabase
            database.librarySourceDao().insert(source())
            assertNotNull(database.librarySourceDao().find("source-1"))
        } finally {
            database.close()
        }
    }

    private fun source() = LibrarySourceEntity(
        sourceId = "source-1",
        sourceType = LibrarySourceType.SAF_TREE,
        rootUri = "content://tree/music",
        volumeName = null,
        persistedPermissionFlags = 3,
        permissionState = SourcePermissionState.GRANTED,
        availability = SourceAvailability.AVAILABLE,
        createdAtEpochMs = 1_000,
        updatedAtEpochMs = 1_000,
    )
}
