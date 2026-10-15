package com.theemrock.smoothmedia.catalog.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.catalog.database.entity.CatalogLifecycle
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import com.theemrock.smoothmedia.metadata.MetadataFieldStatus
import com.theemrock.smoothmedia.metadata.MetadataOverrideField
import com.theemrock.smoothmedia.metadata.MetadataNormalizer
import com.theemrock.smoothmedia.metadata.RawAudioMetadata
import com.theemrock.smoothmedia.metadata.RoomTrackMetadataRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetadataPersistenceRoomTest {
    private lateinit var database: CatalogDatabase
    private lateinit var repository: RoomTrackMetadataRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        database.trackDao().insertTrack(
            TrackEntity("track-1", CatalogLifecycle.ACTIVE, 1_000L, 1_000L),
        )
        repository = RoomTrackMetadataRepository(database.trackMetadataDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun fileMetadata_isPersistedWithRawAndNormalizedValues() {
        val raw = RawAudioMetadata(title = "  File title  ", artist = "Artist", year = "2024")
        repository.replaceFileMetadata("track-1", raw, MetadataNormalizer().normalize(raw), 2_000L)

        val snapshot = repository.find("track-1")!!
        assertEquals("  File title  ", snapshot.raw.title)
        assertEquals("File title", snapshot.fileMetadata.title.value)
        assertEquals(2024, snapshot.fileMetadata.year.value)
    }

    @Test
    fun fileRefresh_preservesOverride_andClearRevealsLatestFileValue() {
        val first = RawAudioMetadata(title = "Embedded one", artist = "Artist")
        repository.replaceFileMetadata("track-1", first, MetadataNormalizer().normalize(first), 2_000L)
        assertTrue(repository.setOverride("track-1", MetadataOverrideField.TITLE, "User title", 2_100L))

        val refreshed = RawAudioMetadata(title = "Embedded two", artist = "Artist")
        repository.replaceFileMetadata("track-1", refreshed, MetadataNormalizer().normalize(refreshed), 3_000L)
        assertEquals("Embedded two", repository.find("track-1")!!.fileMetadata.title.value)
        assertEquals("User title", repository.find("track-1")!!.effective.title.value)

        assertTrue(repository.clearOverride("track-1", MetadataOverrideField.TITLE))
        assertEquals("Embedded two", repository.find("track-1")!!.effective.title.value)
    }

    @Test
    fun invalidOrBlankOverride_isRejectedWithoutFabricatingMetadata() {
        assertFalse(repository.setOverride("track-1", MetadataOverrideField.TITLE, " ", 2_000L))
        assertFalse(repository.setOverride("track-1", MetadataOverrideField.YEAR, "unknown", 2_000L))
        assertEquals(null, repository.find("track-1"))
        assertEquals(MetadataFieldStatus.MISSING, MetadataNormalizer().normalize(RawAudioMetadata()).title.status)
    }
}
