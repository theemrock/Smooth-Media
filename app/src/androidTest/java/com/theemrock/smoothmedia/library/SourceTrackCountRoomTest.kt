package com.theemrock.smoothmedia.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.CatalogLifecycle
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackLocationEntity
import com.theemrock.smoothmedia.catalog.database.entity.LocatorBindingEntity
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceTrackCountRoomTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        CatalogDatabase::class.java,
    ).allowMainThreadQueries().build()

    @After
    fun closeDatabase() = database.close()

    @Test
    fun sourceCount_deduplicatesLocations_withoutUsingGlobalTrackCount() {
        database.librarySourceDao().insert(
            LibrarySourceEntity(
                "source-1", LibrarySourceType.SAF_TREE, "content://tree/1", null, 1,
                SourcePermissionState.GRANTED, SourceAvailability.AVAILABLE, 1, 1,
                SourceSelectionState.ACTIVE,
            ),
        )
        database.trackDao().insertTrack(TrackEntity("track-1", CatalogLifecycle.ACTIVE, 1, 1))
        database.trackDao().insertTrack(TrackEntity("track-2", CatalogLifecycle.ACTIVE, 1, 1))
        location("location-1a", "track-1", "content://song/1a")
        location("location-1b", "track-1", "content://song/1b")
        location("location-2", "track-2", "content://song/2")

        assertEquals(2, database.libraryDao().countActiveTracksForSource("source-1"))
        assertEquals(2, RoomLibraryRepository(database.libraryDao()).countTracks())
        assertEquals(3, database.trackDao().listLocations().size)
    }

    private fun location(id: String, trackId: String, locator: String) {
        database.trackDao().insertLocation(
            TrackLocationEntity(
                id, trackId, "source-1", hash(), hash(), AudioFormat.MP3,
                LocationAvailability.AVAILABLE, null, null, 1, 1, null, null, null, null, 1, 1,
            ),
        )
        database.locatorBindingDao().insert(LocatorBindingEntity(locator, id, "source-1", 1))
    }

    private fun hash() = StoredFileHash("SHA-256", 1, "hash")
}
