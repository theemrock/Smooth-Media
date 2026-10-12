package com.theemrock.smoothmedia.catalog.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanCandidateState
import com.theemrock.smoothmedia.catalog.database.entity.ScanCheckpointCoverage
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionEntity
import com.theemrock.smoothmedia.catalog.database.entity.ScanSessionStatus
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceScanCheckpointEntity
import com.theemrock.smoothmedia.catalog.repository.LeaseRecoveryResult
import com.theemrock.smoothmedia.catalog.repository.RoomLibrarySourceRepository
import com.theemrock.smoothmedia.catalog.repository.RoomScanStateRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanStateRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: CatalogDatabase
    private lateinit var repository: RoomScanStateRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = inMemoryDatabase()
        repository = RoomScanStateRepository(database)
        database.librarySourceDao().insert(source())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sourcePermissionState_isPersistedSeparatelyFromAvailability() {
        val sourceRepository = RoomLibrarySourceRepository(database)

        assertTrue(
            sourceRepository.updateState(
                sourceId = "source-1",
                persistedPermissionFlags = 0,
                permissionState = SourcePermissionState.LOST,
                availability = SourceAvailability.PERMISSION_LOST,
                updatedAtEpochMs = 2_000,
            ),
        )

        val stored = sourceRepository.find("source-1")!!
        assertEquals(SourcePermissionState.LOST, stored.permissionState)
        assertEquals(SourceAvailability.PERMISSION_LOST, stored.availability)
        assertEquals(0, stored.persistedPermissionFlags)
    }

    @Test
    fun scanCheckpoint_isPersisted() {
        repository.startSession(session("scan-1"), listOf(checkpoint("scan-1")))
        repository.updateCheckpoint(
            sessionId = "scan-1",
            sourceId = "source-1",
            continuationToken = "cursor-42",
            discoveredCount = 42,
            processedCount = 20,
            coverage = ScanCheckpointCoverage.PARTIAL,
            updatedAtEpochMs = 2_000,
        )

        val stored = repository.findCheckpoint("scan-1", "source-1")!!
        assertEquals("cursor-42", stored.continuationToken)
        assertEquals(42, stored.discoveredCount)
        assertEquals(20, stored.processedCount)
    }

    @Test
    fun completedCandidate_survivesRepositoryAndDatabaseReopen() {
        val databaseName = "scan-persistence-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val first = fileDatabase(databaseName)
        first.librarySourceDao().insert(source())
        val firstRepository = RoomScanStateRepository(first)
        firstRepository.startSession(session("scan-1"), listOf(checkpoint("scan-1")))
        firstRepository.recordCandidate(candidate("scan-1", state = ScanCandidateState.COMPLETED))
        first.close()

        val reopened = fileDatabase(databaseName)
        try {
            val stored = RoomScanStateRepository(reopened)
                .findCandidate("scan-1", "source-1", "candidate-1")
            assertNotNull(stored)
            assertEquals(ScanCandidateState.COMPLETED, stored!!.state)
        } finally {
            reopened.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun partialCheckpoint_neverAllowsMissingReconciliation() {
        repository.startSession(session("scan-1"), listOf(checkpoint("scan-1")))
        database.scanDao().updateSessionStatus("scan-1", ScanSessionStatus.COMPLETED, 2_000, 2_000)

        assertFalse(repository.isMissingReconciliationAllowed("scan-1", "source-1"))
    }

    @Test
    fun completedSessionAndCheckpoint_allowMissingReconciliation() {
        repository.startSession(session("scan-1"), listOf(checkpoint("scan-1")))
        repository.updateCheckpoint(
            sessionId = "scan-1",
            sourceId = "source-1",
            continuationToken = null,
            discoveredCount = 10,
            processedCount = 10,
            coverage = ScanCheckpointCoverage.COMPLETE,
            updatedAtEpochMs = 2_000,
        )
        repository.completeSession("scan-1", completedAtEpochMs = 2_000)

        assertTrue(repository.isMissingReconciliationAllowed("scan-1", "source-1"))
    }

    @Test
    fun secondActiveScan_isRejectedAndRolledBack() {
        repository.startSession(session("scan-1"), listOf(checkpoint("scan-1")))

        assertThrows(SQLiteConstraintException::class.java) {
            repository.startSession(session("scan-2"), listOf(checkpoint("scan-2")))
        }

        assertNull(repository.findSession("scan-2"))
        assertEquals("scan-1", database.scanDao().findActiveLease()!!.sessionId)
    }

    @Test
    fun runningLease_isPreservedForResumeAndNewSessionIsNotCreated() {
        repository.startSession(session("scan-1"), listOf(checkpoint("scan-1")))

        val result = repository.recoverStaleLeaseAndStart(
            session = session("scan-2"),
            checkpoints = listOf(checkpoint("scan-2")),
        )

        assertEquals(LeaseRecoveryResult.ResumeRequired("scan-1"), result)
        assertNull(repository.findSession("scan-2"))
        assertEquals("scan-1", database.scanDao().findActiveLease()!!.sessionId)
    }

    @Test
    fun terminalStaleLease_isRecoveredAtomicallyBeforeNewScanStarts() {
        repository.startSession(session("scan-1"), listOf(checkpoint("scan-1")))
        database.scanDao().updateSessionStatus("scan-1", ScanSessionStatus.FAILED, 2_000, 2_000)

        val result = repository.recoverStaleLeaseAndStart(
            session = session("scan-2"),
            checkpoints = listOf(checkpoint("scan-2")),
        )

        assertEquals(LeaseRecoveryResult.Started("scan-2", recoveredStaleLease = true), result)
        assertEquals("scan-2", database.scanDao().findActiveLease()!!.sessionId)
        assertNotNull(repository.findSession("scan-1"))
        assertNotNull(repository.findSession("scan-2"))
    }

    private fun inMemoryDatabase() = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java)
        .addCallback(CatalogDatabaseCallback)
        .build()

    private fun fileDatabase(name: String) = Room.databaseBuilder(context, CatalogDatabase::class.java, name)
        .addCallback(CatalogDatabaseCallback)
        .addMigrations(*CatalogMigrations.ALL)
        .build()

    private fun session(id: String) = ScanSessionEntity(
        sessionId = id,
        status = ScanSessionStatus.RUNNING,
        startedAtEpochMs = 1_000,
        updatedAtEpochMs = 1_000,
        completedAtEpochMs = null,
    )

    private fun checkpoint(sessionId: String) = SourceScanCheckpointEntity(
        sessionId = sessionId,
        sourceId = "source-1",
        continuationToken = null,
        discoveredCount = 0,
        processedCount = 0,
        coverage = ScanCheckpointCoverage.PARTIAL,
        updatedAtEpochMs = 1_000,
    )

    private fun candidate(
        sessionId: String,
        state: ScanCandidateState,
    ) = ScanCandidateEntity(
        sessionId = sessionId,
        sourceId = "source-1",
        candidateKey = "candidate-1",
        state = state,
        locationId = null,
        updatedAtEpochMs = 1_000,
    )

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
