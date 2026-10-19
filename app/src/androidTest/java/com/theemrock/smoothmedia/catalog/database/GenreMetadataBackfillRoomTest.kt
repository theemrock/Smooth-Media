package com.theemrock.smoothmedia.catalog.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.catalog.database.entity.CatalogLifecycle
import com.theemrock.smoothmedia.catalog.database.entity.GenreNodeEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackMetadataEntity
import com.theemrock.smoothmedia.genre.RoomGenreRepository
import com.theemrock.smoothmedia.metadata.GenreMetadataBackfill
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenreMetadataBackfillRoomTest {
    private lateinit var database: CatalogDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        database.trackDao().insertTrack(track("track-1"))
        database.trackMetadataDao().upsert(metadata("track-1", "Rock\u001FMetal"))
        database.genreDao().upsertNode(GenreNodeEntity("rock", "Rock", "rock", "USER", null, true))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun backfillUsesStoredGenresIsIdempotentAndPreservesUserOverride() {
        val repository = RoomGenreRepository(database)
        repository.assignGenre("track-1", "rock", isPrimary = true, userOverride = true)

        val first = GenreMetadataBackfill(database.trackMetadataDao(), repository).run()
        val second = GenreMetadataBackfill(database.trackMetadataDao(), repository).run()

        assertEquals(1, first.processedTracks)
        assertTrue(first.createdAssignments >= 2)
        assertEquals(1, second.processedTracks)
        assertEquals(2, database.genreDao().allNodes().size)
        assertEquals(2, database.genreDao().assignmentsForTrack("track-1").size)
        assertTrue(database.genreDao().assignmentsForTrack("track-1").first { it.genreId == "rock" }.userOverride)
    }

    private fun track(id: String) = TrackEntity(id, CatalogLifecycle.ACTIVE, 1_000L, 1_000L)

    private fun metadata(trackId: String, genres: String) = TrackMetadataEntity(
        trackId = trackId,
        rawTitle = "Song",
        rawArtist = "Artist",
        rawAlbum = "Album",
        rawAlbumArtist = "Artist",
        rawGenre = genres,
        rawTrackNumber = null,
        rawDiscNumber = null,
        rawYear = null,
        rawDurationMs = null,
        normalizedTitle = "Song",
        normalizedTitleStatus = "AVAILABLE",
        normalizedArtist = "Artist",
        normalizedArtistStatus = "AVAILABLE",
        normalizedAlbum = "Album",
        normalizedAlbumStatus = "AVAILABLE",
        normalizedAlbumArtist = "Artist",
        normalizedAlbumArtistStatus = "AVAILABLE",
        normalizedGenres = genres,
        normalizedGenresStatus = "AVAILABLE",
        normalizedTrackNumber = null,
        normalizedTrackNumberStatus = "MISSING",
        normalizedDiscNumber = null,
        normalizedDiscNumberStatus = "MISSING",
        normalizedYear = null,
        normalizedYearStatus = "MISSING",
        normalizedDurationMs = null,
        normalizedDurationMsStatus = "MISSING",
        updatedAtEpochMs = 1_000L,
    )
}
