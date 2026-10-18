package com.theemrock.smoothmedia.catalog.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.catalog.database.entity.CatalogLifecycle
import com.theemrock.smoothmedia.catalog.database.entity.GenreNodeEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackGenreAssignmentEntity
import com.theemrock.smoothmedia.genre.GenreBuildResult
import com.theemrock.smoothmedia.genre.GenreNode
import com.theemrock.smoothmedia.genre.RoomGenreRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenreRoomRepositoryTest {
    private lateinit var database: CatalogDatabase
    private lateinit var repository: RoomGenreRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        database.trackDao().insertTrack(track("track-1"))
        repository = RoomGenreRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun fullSnapshotReplacesEdgesAndInvalidSnapshotLeavesExistingGraphUntouched() {
        repository.saveFullHierarchySnapshot(
            listOf(GenreNode("rock"), GenreNode("metal", setOf("rock"))),
        )

        val invalid = repository.saveFullHierarchySnapshot(
            listOf(GenreNode("rock"), GenreNode("metal", setOf("missing"))),
        )

        assertTrue(invalid is GenreBuildResult.Invalid)
        assertEquals(listOf("metal"), repository.childrenOf("rock"))
    }

    @Test
    fun partialUpdatePreservesUnmentionedEdgesAndSupportsMultipleParents() {
        repository.saveFullHierarchySnapshot(
            listOf(
                GenreNode("rock"),
                GenreNode("jazz"),
                GenreNode("fusion", setOf("rock")),
            ),
        )

        repository.applyPartialHierarchyUpdate(
            nodes = listOf(GenreNode("fusion", setOf("rock", "jazz"))),
        )

        assertEquals(listOf("fusion"), repository.childrenOf("rock"))
        assertEquals(listOf("fusion"), repository.childrenOf("jazz"))

        repository.applyPartialHierarchyUpdate(listOf(GenreNode("fusion", setOf("jazz"))))
        assertEquals(emptyList<String>(), repository.childrenOf("rock"))
        assertEquals(listOf("fusion"), repository.childrenOf("jazz"))
    }

    @Test
    fun explicitPrimaryChangeDemotesOldPrimaryAndImplicitSecondPrimaryIsRejected() {
        repository.saveFullHierarchySnapshot(listOf(GenreNode("rock"), GenreNode("metal")))
        repository.assignGenre("track-1", "rock", isPrimary = true, userOverride = true)

        val rejected = repository.assignGenre("track-1", "metal", isPrimary = true, userOverride = true)
        assertTrue(rejected.isRejected)

        repository.changePrimaryGenre("track-1", "metal", userOverride = true)
        val assignments = database.genreDao().assignmentsForTrack("track-1")
        assertEquals(false, assignments.single { it.genreId == "rock" }.isPrimary)
        assertEquals(true, assignments.single { it.genreId == "metal" }.isPrimary)
    }

    @Test
    fun clearingPrimaryKeepsMembershipAndDoesNotChooseAnotherPrimary() {
        repository.saveFullHierarchySnapshot(listOf(GenreNode("rock"), GenreNode("metal")))
        repository.assignGenre("track-1", "rock", isPrimary = true, userOverride = true)
        repository.assignGenre("track-1", "metal", isPrimary = false, userOverride = true)

        repository.clearPrimaryGenre("track-1", "rock")

        val assignments = database.genreDao().assignmentsForTrack("track-1")
        assertEquals(setOf("rock", "metal"), assignments.map { it.genreId }.toSet())
        assertTrue(assignments.none { it.isPrimary })
    }

    @Test
    fun metadataUpsertPreservesUserOverride() {
        database.genreDao().upsertNode(GenreNodeEntity("rock", "My Rock", "my rock", "USER", null, true))

        repository.saveFullHierarchySnapshot(listOf(GenreNode("rock")))

        val node = database.genreDao().allNodes().single()
        assertEquals("My Rock", node.displayName)
        assertEquals(true, node.userOverride)
    }

    @Test
    fun batchWithSecondPrimaryRollsBackAllAssignments() {
        repository.saveFullHierarchySnapshot(listOf(GenreNode("rock"), GenreNode("metal")))

        val result = repository.assignGenresBatch(
            listOf(
                TrackGenreAssignmentEntity("track-1", "rock", true, "USER", null, true),
                TrackGenreAssignmentEntity("track-1", "metal", true, "USER", null, true),
            ),
        )

        assertTrue(result.isRejected)
        assertEquals(emptyList<Any>(), database.genreDao().assignmentsForTrack("track-1"))
    }

    private fun track(id: String) = TrackEntity(id, CatalogLifecycle.ACTIVE, 1_000L, 1_000L)
}
