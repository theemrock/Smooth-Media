package com.theemrock.smoothmedia.catalog.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SafDocumentEntity
import com.theemrock.smoothmedia.catalog.database.entity.CatalogLifecycle
import com.theemrock.smoothmedia.catalog.database.entity.LocatorBindingEntity
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackLocationEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafDocumentDaoTest {
    private lateinit var database: CatalogDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CatalogDatabase::class.java,
        ).build()
        database.librarySourceDao().insert(source())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun rootRowsExcludeNestedDirectories_andUpsertIsIdempotent() {
        val dao = database.safDocumentDao()
        dao.upsert(document("root", null, "Music", true))
        dao.upsert(document("nested", "root", "Albums", true))
        dao.upsert(document("song", "nested", "track.mp3", false))
        dao.upsert(document("nested", "root", "Albums", true))

        val roots = dao.loadDirectoryRows(0, 20)
        assertEquals(listOf("source-1\u001Eroot"), roots.map { it.key })
        assertEquals(1, dao.countDirectories())
        assertEquals(0, roots.single().trackCount)
        assertEquals(listOf("nested", "song"), dao.children("source-1", "root").map { it.documentId })
        assertTrue(dao.children("source-1", "nested").single().documentId == "song")

        database.trackDao().insertTrack(TrackEntity("track-1", CatalogLifecycle.ACTIVE, 1, 1))
        database.trackDao().insertLocation(
            TrackLocationEntity(
                "location-1", "track-1", "source-1", hash(), hash(), AudioFormat.MP3,
                LocationAvailability.AVAILABLE, null, null, 1, 1, null, null, null, null, 1, 1,
            ),
        )
        database.locatorBindingDao().insert(LocatorBindingEntity("content://provider/document/song", "location-1", "source-1", 1))
        assertEquals(1, dao.loadDirectoryRows(0, 20).single().trackCount)
    }

    private fun source() = LibrarySourceEntity(
        sourceId = "source-1",
        sourceType = LibrarySourceType.SAF_TREE,
        rootUri = "content://provider/tree/music",
        volumeName = "Music",
        persistedPermissionFlags = 1,
        permissionState = SourcePermissionState.GRANTED,
        availability = SourceAvailability.AVAILABLE,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )

    private fun document(id: String, parent: String?, name: String, directory: Boolean) = SafDocumentEntity(
        sourceId = "source-1",
        documentId = id,
        uri = "content://provider/document/$id",
        parentDocumentId = parent,
        displayName = name,
        isDirectory = directory,
        available = true,
        updatedAtEpochMs = 1,
    )

    private fun hash() = StoredFileHash("SHA-256", 1, "digest")
}
