package com.theemrock.smoothmedia.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.CatalogExclusionEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.LocatorBindingEntity
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackLocationEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackMetadataEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackMetadataOverrideEntity
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomLibraryRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        CatalogDatabase::class.java,
    ).allowMainThreadQueries().build()

    @After
    fun closeDatabase() = database.close()

    @Test
    fun repository_listsOneTrackForMultipleLocations_andAppliesOverride() {
        seedSource()
        seedTrack("track-1")
        seedLocation("location-1", "track-1", LocationAvailability.AVAILABLE, "content://song/1")
        seedLocation("location-2", "track-1", LocationAvailability.MISSING, "content://song/2")
        database.locatorBindingDao().insert(LocatorBindingEntity("content://song/1", "location-1", "source-1", 1))
        database.locatorBindingDao().insert(LocatorBindingEntity("content://song/2", "location-2", "source-1", 1))
        database.trackMetadataDao().upsert(metadata("track-1"))
        database.trackMetadataDao().upsertOverride(TrackMetadataOverrideEntity("track-1", "TITLE", "Edited title", 2))

        val result = RoomLibraryRepository(database.libraryDao()).loadPage(0, 10)

        assertEquals(1, result.songs.size)
        assertEquals("Edited title", result.songs.single().title)
        assertEquals(2, result.songs.single().locationCount)
        assertEquals(LibraryLocationState.AVAILABLE, result.songs.single().locationState)
    }

    @Test
    fun repository_hidesTrack_whenEveryLocationIsExcluded_butKeepsMissingTrack() {
        seedSource()
        seedTrack("track-excluded")
        seedTrack("track-missing")
        seedLocation("location-excluded", "track-excluded", LocationAvailability.AVAILABLE, "content://song/excluded")
        seedLocation("location-missing", "track-missing", LocationAvailability.MISSING, "content://song/missing")
        database.locatorBindingDao().insert(LocatorBindingEntity("content://song/excluded", "location-excluded", "source-1", 1))
        database.locatorBindingDao().insert(LocatorBindingEntity("content://song/missing", "location-missing", "source-1", 1))
        database.catalogExclusionDao().insert(
            CatalogExclusionEntity("exclusion-1", "source-1", "content://song/excluded", hash(), 1),
        )

        val result = RoomLibraryRepository(database.libraryDao()).loadPage(0, 10)

        assertEquals(listOf("track-missing"), result.songs.map { it.trackId })
        assertEquals(1, RoomLibraryRepository(database.libraryDao()).countTracks())
        assertEquals(LibraryMetadataState.MISSING, result.songs.single().metadataState)
    }

    @Test
    fun repository_ordersTracksWithMissingTitle_afterTracksWithMetadata() {
        seedTrack("track-missing")
        seedTrack("track-available")
        database.trackMetadataDao().upsert(metadata("track-available"))

        val result = RoomLibraryRepository(database.libraryDao()).loadPage(0, 1)

        assertEquals(listOf("track-available"), result.songs.map { it.trackId })
        assertEquals("Title", result.songs.single().title)
    }

    @Test
    fun repository_marksOnlyTracksWithoutAvailableLocation_asUnavailable() {
        seedSource()
        seedTrack("track-unavailable")
        seedTrack("track-permission")
        seedTrack("track-both-missing")
        seedTrack("track-no-location")
        seedLocation("location-unavailable", "track-unavailable", LocationAvailability.UNAVAILABLE, "content://song/unavailable")
        seedLocation("location-permission", "track-permission", LocationAvailability.PERMISSION_LOST, "content://song/permission")
        seedLocation("location-missing", "track-both-missing", LocationAvailability.MISSING, "content://song/missing")
        seedLocation("location-unavailable-2", "track-both-missing", LocationAvailability.UNAVAILABLE, "content://song/unavailable-2")

        val songs = RoomLibraryRepository(database.libraryDao()).loadPage(0, 10).songs.associateBy { it.trackId }

        assertEquals(LibraryLocationState.UNAVAILABLE, songs.getValue("track-unavailable").locationState)
        assertEquals(LibraryLocationState.PERMISSION_LOST, songs.getValue("track-permission").locationState)
        assertEquals(LibraryLocationState.MISSING, songs.getValue("track-both-missing").locationState)
        assertEquals(LibraryLocationState.UNKNOWN, songs.getValue("track-no-location").locationState)
        assertEquals(false, songs.getValue("track-both-missing").isAccessible)
    }

    @Test
    fun repository_groupsAlbumByEffectiveAlbumArtist_withoutCountingLocationsTwice() {
        seedSource()
        seedTrack("track-1")
        seedTrack("track-2")
        seedLocation("location-1", "track-1", LocationAvailability.AVAILABLE, "content://song/1")
        seedLocation("location-2", "track-1", LocationAvailability.MISSING, "content://song/2")
        database.locatorBindingDao().insert(LocatorBindingEntity("content://song/1", "location-1", "source-1", 1))
        database.locatorBindingDao().insert(LocatorBindingEntity("content://song/2", "location-2", "source-1", 1))
        database.trackMetadataDao().upsert(metadata("track-1"))
        database.trackMetadataDao().upsert(metadata("track-2"))

        val result = RoomLibraryRepository(database.libraryDao())
            .loadCollections(LibraryCollectionType.ALBUMS, 0, 10)

        assertEquals(1, result.collections.size)
        assertEquals(2, result.collections.single().trackCount)
        assertEquals("Album", result.collections.single().title)
    }

    @Test
    fun repository_artistStats_deduplicateTrackLocations_andCountFormatsPerTrack() {
        seedSource()
        seedTrack("track-1")
        seedTrack("track-2")
        seedLocation("location-1a", "track-1", LocationAvailability.AVAILABLE, "content://song/1a", AudioFormat.MP3)
        seedLocation("location-1b", "track-1", LocationAvailability.AVAILABLE, "content://song/1b", AudioFormat.FLAC)
        seedLocation("location-2", "track-2", LocationAvailability.AVAILABLE, "content://song/2", AudioFormat.MP3)
        database.locatorBindingDao().insert(LocatorBindingEntity("content://song/1a", "location-1a", "source-1", 1))
        database.locatorBindingDao().insert(LocatorBindingEntity("content://song/1b", "location-1b", "source-1", 1))
        database.locatorBindingDao().insert(LocatorBindingEntity("content://song/2", "location-2", "source-1", 1))
        database.trackMetadataDao().upsert(metadata("track-1", album = "Album A"))
        database.trackMetadataDao().upsert(metadata("track-2", album = "Album B"))

        val artist = RoomLibraryRepository(database.libraryDao())
            .loadCollections(LibraryCollectionType.ARTISTS, 0, 10)
            .collections.single()

        assertEquals(2, artist.trackCount)
        assertEquals(2, artist.albumCount)
        assertEquals(2, artist.mp3Count)
        assertEquals(1, artist.flacCount)
    }

    private fun seedSource() = database.librarySourceDao().insert(
        LibrarySourceEntity(
            "source-1", LibrarySourceType.SAF_TREE, "content://tree/1", null, 1,
            SourcePermissionState.GRANTED, SourceAvailability.AVAILABLE, 1, 1,
            SourceSelectionState.ACTIVE,
        ),
    )

    private fun seedTrack(id: String) = database.trackDao().insertTrack(
        TrackEntity(id, com.theemrock.smoothmedia.catalog.database.entity.CatalogLifecycle.ACTIVE, 1, 1),
    )

    private fun seedLocation(
        id: String,
        trackId: String,
        availability: LocationAvailability,
        locator: String,
        format: AudioFormat = AudioFormat.MP3,
    ) =
        database.trackDao().insertLocation(
            TrackLocationEntity(
                id, trackId, "source-1", hash(), hash(), format, availability,
                null, null, 1, 1, null, null, null, null, 1, 1,
            ),
        )

    private fun metadata(trackId: String, album: String = "Album") = TrackMetadataEntity(
        trackId = trackId,
        rawTitle = "Title",
        rawArtist = "Artist",
        rawAlbum = album,
        rawAlbumArtist = null,
        rawGenre = null,
        rawTrackNumber = null,
        rawDiscNumber = null,
        rawYear = null,
        rawDurationMs = "1000",
        normalizedTitle = "Title",
        normalizedTitleStatus = "AVAILABLE",
        normalizedArtist = "Artist",
        normalizedArtistStatus = "AVAILABLE",
        normalizedAlbum = album,
        normalizedAlbumStatus = "AVAILABLE",
        normalizedAlbumArtist = null,
        normalizedAlbumArtistStatus = "MISSING",
        normalizedGenres = null,
        normalizedGenresStatus = "MISSING",
        normalizedTrackNumber = null,
        normalizedTrackNumberStatus = "MISSING",
        normalizedDiscNumber = null,
        normalizedDiscNumberStatus = "MISSING",
        normalizedYear = null,
        normalizedYearStatus = "MISSING",
        normalizedDurationMs = 1000L,
        normalizedDurationMsStatus = "AVAILABLE",
        updatedAtEpochMs = 1,
    )

    private fun hash() = StoredFileHash("SHA-256", 1, "digest")
}
