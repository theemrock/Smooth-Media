package com.theemrock.smoothmedia.catalog.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun versionOneToTwo_addsActiveSelectionStateAndPreservesAllTables() {
        migrationHelper.createDatabase(databaseName, 1).apply {
            insertVersionOneFixture(this)
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            2,
            true,
            *CatalogMigrations.ALL,
        )
        try {
            assertEquals(SourceSelectionState.ACTIVE.name, migrated.singleString("SELECT selection_state FROM library_sources"))
            listOf(
                "tracks",
                "library_sources",
                "track_locations",
                "locator_bindings",
                "scan_sessions",
                "active_scan_leases",
                "source_scan_checkpoints",
                "scan_candidates",
                "catalog_exclusions",
            ).forEach { table ->
                assertEquals("row missing after migration in $table", 1, migrated.singleInt("SELECT COUNT(*) FROM $table"))
            }
        } finally {
            migrated.close()
        }
    }

    @Test
    fun versionOneToThree_addsMetadataTablesAndPreservesTrackData() {
        migrationHelper.createDatabase(databaseName, 1).apply {
            insertVersionOneFixture(this)
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            *CatalogMigrations.ALL,
        )
        try {
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM tracks"))
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM track_locations"))
            assertEquals(0, migrated.singleInt("SELECT COUNT(*) FROM track_metadata"))
            assertEquals(0, migrated.singleInt("SELECT COUNT(*) FROM track_metadata_overrides"))
        } finally {
            migrated.close()
        }
    }

    @Test
    fun versionTwoToThree_addsMetadataTablesAndPreservesV2Catalog() {
        migrationHelper.createDatabase(databaseName, 2).apply {
            insertVersionTwoFixture(this)
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            *CatalogMigrations.ALL,
        )
        try {
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM tracks"))
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM track_locations"))
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM locator_bindings"))
            assertEquals(0, migrated.singleInt("SELECT COUNT(*) FROM track_metadata"))
            assertEquals(0, migrated.singleInt("SELECT COUNT(*) FROM track_metadata_overrides"))
        } finally {
            migrated.close()
        }
    }

    @Test
    fun versionThreeToFour_addsLyricsCacheWithoutChangingTracks() {
        migrationHelper.createDatabase(databaseName, 3).apply {
            insertVersionTwoFixture(this)
            execSQL("INSERT INTO track_metadata (trackId, normalizedTitleStatus, normalizedArtistStatus, normalizedAlbumStatus, normalizedAlbumArtistStatus, normalizedGenresStatus, normalizedTrackNumberStatus, normalizedDiscNumberStatus, normalizedYearStatus, normalizedDurationMsStatus, updatedAtEpochMs) VALUES ('track-1','MISSING','MISSING','MISSING','MISSING','MISSING','MISSING','MISSING','MISSING','MISSING',1000)")
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(databaseName, 4, true, *CatalogMigrations.ALL)
        try {
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM tracks"))
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM lyrics_cache"))
            migrated.execSQL("INSERT INTO lyrics_cache VALUES ('track-1','SYNCED','[00:01.00]Hello','LOCAL_SIDECAR','folder/song.lrc',2000)")
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM lyrics_cache WHERE trackId='track-1'"))
        } finally {
            migrated.close()
        }
    }

    @Test
    fun versionFourToFive_addsSafDocumentsWithoutChangingCatalog() {
        migrationHelper.createDatabase(databaseName, 4).apply {
            insertVersionTwoFixture(this)
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(databaseName, 5, true, *CatalogMigrations.ALL)
        try {
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM tracks"))
            migrated.execSQL("INSERT INTO saf_documents VALUES ('source-1','root',NULL,'Music',1,1,2000)")
            migrated.execSQL("INSERT INTO saf_documents VALUES ('source-1','file','root','song.mp3',0,1,2000)")
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM saf_documents WHERE parentDocumentId='root'"))
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM track_locations"))
        } finally {
            migrated.close()
        }
    }

    @Test
    fun versionFiveToSix_addsProviderUriWithoutChangingDocumentIdentity() {
        migrationHelper.createDatabase(databaseName, 5).apply {
            insertVersionTwoFixture(this)
            execSQL("INSERT INTO saf_documents VALUES ('source-1','root',NULL,'Music',1,1,2000)")
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(databaseName, 6, true, *CatalogMigrations.ALL)
        try {
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM saf_documents WHERE documentId='root'"))
            assertEquals("content://provider/tree/music", migrated.singleString("SELECT uri FROM saf_documents WHERE documentId='root'"))
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM tracks"))
        } finally {
            migrated.close()
        }
    }

    @Test
    fun versionSixToSeven_addsEmptyGenreDagWithoutChangingCatalog() {
        migrationHelper.createDatabase(databaseName, 6).apply {
            insertVersionTwoFixture(this)
            execSQL("INSERT INTO saf_documents VALUES ('source-1','root','content://provider/tree/music',NULL,'Music',1,1,2000)")
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(databaseName, 7, true, *CatalogMigrations.ALL)
        try {
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM tracks"))
            assertEquals(0, migrated.singleInt("SELECT COUNT(*) FROM genre_nodes"))
            assertEquals(0, migrated.singleInt("SELECT COUNT(*) FROM genre_edges"))
            assertEquals(0, migrated.singleInt("SELECT COUNT(*) FROM track_genre_assignments"))
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM saf_documents"))
        } finally {
            migrated.close()
        }
    }

    private fun insertVersionOneFixture(database: SupportSQLiteDatabase) {
        database.execSQL("INSERT INTO tracks VALUES ('track-1', 'ACTIVE', 1000, 1000)")
        database.execSQL(
            "INSERT INTO library_sources VALUES ('source-1', 'SAF_TREE', 'content://provider/tree/music', NULL, 1, 'GRANTED', 'AVAILABLE', 1000, 1000)",
        )
        database.execSQL(
            """
            INSERT INTO track_locations VALUES (
                'location-1', 'track-1', 'source-1', 'FLAC', 'AVAILABLE', NULL, NULL,
                42, 900, 180000, 900000, 96000, 2, 1000, 1000,
                'SHA-256', 1, 'digest', 'SHA-256', 1, 'digest'
            )
            """.trimIndent(),
        )
        database.execSQL("INSERT INTO locator_bindings VALUES ('content://provider/document/track', 'location-1', 'source-1', 1000)")
        database.execSQL("INSERT INTO scan_sessions VALUES ('scan-1', 'RUNNING', 1000, 1000, NULL)")
        database.execSQL("INSERT INTO active_scan_leases VALUES (1, 'scan-1')")
        database.execSQL("INSERT INTO source_scan_checkpoints VALUES ('scan-1', 'source-1', NULL, 1, 0, 'PARTIAL', 1000)")
        database.execSQL("INSERT INTO scan_candidates VALUES ('scan-1', 'source-1', 'candidate-1', 'DISCOVERED', 'location-1', 1000)")
        database.execSQL("INSERT INTO catalog_exclusions VALUES ('exclusion-1', 'source-1', 'content://provider/document/excluded', 1000, 'SHA-256', 1, 'excluded')")
    }

    private fun insertVersionTwoFixture(database: SupportSQLiteDatabase) {
        database.execSQL("INSERT INTO tracks VALUES ('track-1', 'ACTIVE', 1000, 1000)")
        database.execSQL(
            "INSERT INTO library_sources VALUES ('source-1', 'SAF_TREE', 'content://provider/tree/music', NULL, 1, 'GRANTED', 'AVAILABLE', 1000, 1000, 'ACTIVE')",
        )
        database.execSQL(
            """
            INSERT INTO track_locations VALUES (
                'location-1', 'track-1', 'source-1', 'FLAC', 'AVAILABLE', NULL, NULL,
                42, 900, 180000, 900000, 96000, 2, 1000, 1000,
                'SHA-256', 1, 'digest', 'SHA-256', 1, 'digest'
            )
            """.trimIndent(),
        )
        database.execSQL("INSERT INTO locator_bindings VALUES ('content://provider/document/track', 'location-1', 'source-1', 1000)")
        database.execSQL("INSERT INTO scan_sessions VALUES ('scan-1', 'RUNNING', 1000, 1000, NULL)")
        database.execSQL("INSERT INTO active_scan_leases VALUES (1, 'scan-1')")
        database.execSQL("INSERT INTO source_scan_checkpoints VALUES ('scan-1', 'source-1', NULL, 1, 0, 'PARTIAL', 1000)")
        database.execSQL("INSERT INTO scan_candidates VALUES ('scan-1', 'source-1', 'candidate-1', 'DISCOVERED', 'location-1', 1000)")
        database.execSQL("INSERT INTO catalog_exclusions VALUES ('exclusion-1', 'source-1', 'content://provider/document/excluded', 1000, 'SHA-256', 1, 'excluded')")
    }

    private fun SupportSQLiteDatabase.singleString(sql: String): String =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun SupportSQLiteDatabase.singleInt(sql: String): Int =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
