package com.theemrock.smoothmedia.metadata

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.CatalogLifecycle
import com.theemrock.smoothmedia.catalog.database.entity.MetadataRecoverySessionStatus
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetadataRecoverySessionRoomTest {
    private lateinit var database: CatalogDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CatalogDatabase::class.java,
        ).build()
        database.trackDao().insertTrack(TrackEntity("track-1", CatalogLifecycle.ACTIVE, 1L, 1L))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pausePreservesCursorAndResumeCompletesSameSessionWithoutMetadataWrite() {
        val engine = MetadataRecoveryEngine(
            read = { MetadataReadOnlyResult.NoAccessibleLocation },
            current = { null },
        )
        val coordinator = MetadataRecoverySessionCoordinator(database, engine, owner = "test-owner")
        val sessionId = coordinator.start()

        assertEquals(MetadataRecoverySessionStatus.RUNNING, coordinator.runNextBatch(sessionId))
        assertTrue(coordinator.pause(sessionId))
        val paused = database.metadataRecoveryDao().findSession(sessionId)!!
        assertEquals(MetadataRecoverySessionStatus.PAUSED, paused.status)
        assertEquals("track-1", paused.cursorTrackId)
        assertEquals(1, paused.processedTracks)
        assertEquals(9, database.metadataRecoveryDao().countDecisions(sessionId))

        assertTrue(coordinator.resume(sessionId))
        assertEquals(MetadataRecoverySessionStatus.COMPLETED, coordinator.runNextBatch(sessionId))
        val completed = database.metadataRecoveryDao().findSession(sessionId)!!
        assertEquals(MetadataRecoverySessionStatus.COMPLETED, completed.status)
        assertEquals(1, database.trackDao().countActiveTracks())
    }
}
