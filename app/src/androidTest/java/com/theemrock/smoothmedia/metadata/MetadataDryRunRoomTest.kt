package com.theemrock.smoothmedia.metadata

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.CatalogLifecycle
import com.theemrock.smoothmedia.catalog.database.entity.MetadataRecoveryDecisionEntity
import com.theemrock.smoothmedia.catalog.database.entity.MetadataRecoverySessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.MetadataRecoverySessionStatus
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetadataDryRunRoomTest {
    private lateinit var database: CatalogDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CatalogDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun missingMetadataCount_countsOnlyActiveTracks() {
        database.trackDao().insertTrack(TrackEntity("active-missing", CatalogLifecycle.ACTIVE, 1L, 1L))
        database.trackDao().insertTrack(TrackEntity("trashed-missing", CatalogLifecycle.TRASHED, 1L, 1L))

        assertEquals(1, database.trackMetadataDao().countTracksMissingMetadata())
    }

    @Test
    fun dryRun_ignoresNoChangeAndReadErrorDecisionsFromCompletedAudit() {
        database.trackDao().insertTrack(TrackEntity("track-1", CatalogLifecycle.ACTIVE, 1L, 1L))
        database.metadataRecoveryDao().insertSession(session("completed", MetadataRecoverySessionStatus.COMPLETED, 2L))
        database.metadataRecoveryDao().insertSession(session("running", MetadataRecoverySessionStatus.RUNNING, 3L))
        database.metadataRecoveryDao().insertDecisions(
            listOf(
                decision("completed", "track-1", "TITLE", RecoveryDecision.NO_CHANGE),
                decision("completed", "track-1", "ARTIST", RecoveryDecision.READ_ERROR),
            ),
        )

        val completed = database.metadataRecoveryDao().findLatestCompletedSession()
        assertEquals("completed", completed?.sessionId)

        val result = RoomMetadataApplyDryRun(database).evaluate(completed!!.sessionId)

        assertEquals(0, result.evaluated)
        assertEquals(0, result.eligible)
        val applySessions = database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM metadata_apply_sessions",
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        assertEquals(0, applySessions)
        assertTrue(database.metadataRecoveryDao().findSession("running")!!.status == MetadataRecoverySessionStatus.RUNNING)
    }

    private fun session(id: String, status: MetadataRecoverySessionStatus, updatedAt: Long) =
        MetadataRecoverySessionEntity(
            sessionId = id,
            status = status,
            cursorTrackId = null,
            totalTracks = 1,
            processedTracks = 1,
            autoRepairCount = 0,
            noChangeCount = 0,
            reviewRequiredCount = 0,
            unrecoverableCount = 0,
            readErrorCount = 0,
            leaseOwner = null,
            leaseExpiresAtEpochMs = null,
            startedAtEpochMs = updatedAt,
            updatedAtEpochMs = updatedAt,
            completedAtEpochMs = if (status == MetadataRecoverySessionStatus.COMPLETED) updatedAt else null,
        )

    private fun decision(sessionId: String, trackId: String, field: String, decision: RecoveryDecision) =
        MetadataRecoveryDecisionEntity(
            sessionId = sessionId,
            trackId = trackId,
            fieldName = field,
            decision = decision.name,
            evidenceType = "test",
            oldValue = null,
            newValue = null,
            createdAtEpochMs = 2L,
        )
}
