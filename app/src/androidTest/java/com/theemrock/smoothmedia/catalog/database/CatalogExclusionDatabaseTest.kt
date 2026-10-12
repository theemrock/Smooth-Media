package com.theemrock.smoothmedia.catalog.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theemrock.smoothmedia.catalog.database.entity.CatalogExclusionEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash
import com.theemrock.smoothmedia.catalog.exclusion.ConservativeCatalogExclusionPolicy
import com.theemrock.smoothmedia.catalog.exclusion.ExclusionCandidate
import com.theemrock.smoothmedia.catalog.exclusion.ExclusionDecision
import com.theemrock.smoothmedia.catalog.model.FileHash
import com.theemrock.smoothmedia.catalog.model.HashAlgorithmVersion
import com.theemrock.smoothmedia.catalog.repository.RoomCatalogExclusionRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogExclusionDatabaseTest {
    private lateinit var database: CatalogDatabase
    private lateinit var repository: RoomCatalogExclusionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java)
            .addCallback(CatalogDatabaseCallback)
            .build()
        database.librarySourceDao().insert(source())
        repository = RoomCatalogExclusionRepository(
            dao = database.catalogExclusionDao(),
            policy = ConservativeCatalogExclusionPolicy(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ambiguousHashMatch_isReviewRequiredInsteadOfAutomaticExclusion() {
        repository.record(exclusion())

        val decision = repository.evaluate(
            ExclusionCandidate(
                sourceId = "source-1",
                locator = "content://music/copy",
                fileHash = fileHash("hash-1"),
            ),
        )

        assertEquals(ExclusionDecision.ReviewRequired(listOf("exclusion-1")), decision)
    }

    @Test
    fun duplicateSourceLocatorTombstone_isRejected() {
        repository.record(exclusion("exclusion-1"))

        assertThrows(SQLiteConstraintException::class.java) {
            repository.record(exclusion("exclusion-2"))
        }
    }

    private fun exclusion(id: String = "exclusion-1") = CatalogExclusionEntity(
        exclusionId = id,
        sourceId = "source-1",
        locator = "content://music/1",
        fileHash = StoredFileHash("SHA-256", 1, "hash-1"),
        createdAtEpochMs = 1_000,
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

    private fun fileHash(digest: String) = FileHash(
        algorithm = HashAlgorithmVersion("SHA-256", 1),
        digest = digest,
    )
}
