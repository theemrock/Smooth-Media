package com.theemrock.smoothmedia.metadata

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.CatalogDatabaseFactory
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Explicitly invoked pilot for the exact ten tracks selected by P01-09G. */
@RunWith(AndroidJUnit4::class)
class ControlledMetadataWritePilotTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: CatalogDatabase

    @Before
    fun openCatalog() {
        database = CatalogDatabaseFactory.create(context)
    }

    @After
    fun closeCatalog() {
        database.close()
    }

    @Test
    fun writesOnlyPreviewSelectionThroughBoundedBatch() {
        val source = database.librarySourceDao().listAll().firstOrNull { candidate ->
            candidate.sourceType == LibrarySourceType.SAF_TREE &&
                candidate.selectionState == SourceSelectionState.ACTIVE &&
                candidate.permissionState == SourcePermissionState.GRANTED &&
                candidate.availability == SourceAvailability.AVAILABLE &&
                context.contentResolver.persistedUriPermissions.any { permission ->
                    permission.isReadPermission &&
                        Uri.parse(candidate.rootUri).toString().startsWith(permission.uri.toString())
                }
        }
        assumeTrue("No active SAF source with persisted read permission", source != null)

        val selected = database.trackDao().listLocations()
            .asSequence()
            .filter { it.sourceId == source!!.sourceId && it.availability == LocationAvailability.AVAILABLE }
            .filter { it.format == AudioFormat.MP3 || it.format == AudioFormat.FLAC }
            .mapNotNull { location ->
                val binding = database.locatorBindingDao().findByLocation(location.locationId)
                    ?: return@mapNotNull null
                if (database.catalogExclusionDao().isLocatorExcluded(location.sourceId, binding.locator)) {
                    return@mapNotNull null
                }
                PreviewSelection(location.trackId, location.format, Uri.parse(binding.locator))
            }
            .distinctBy { it.trackId }
            .toList()
            .groupBy { it.format }
            .flatMap { (_, candidates) -> candidates.take(MAX_PER_FORMAT) }

        assumeTrue("Preview selection is not available", selected.size == EXPECTED_HASHES.size)
        assertEquals(EXPECTED_HASHES, selected.map { anonymous(it.trackId) }.toSet())

        val before = snapshot()
        val beforeOverrides = database.trackMetadataDao().listOverridesForTracks(selected.map { it.trackId })
        val repository = RoomTrackMetadataRepository(database.trackMetadataDao())
        val enrichment = TrackMetadataEnrichmentUseCase(
            trackDao = database.trackDao(),
            bindingDao = database.locatorBindingDao(),
            exclusionDao = database.catalogExclusionDao(),
            metadata = repository,
            reader = AndroidAudioMetadataReader(context),
        )

        val result = MetadataBatchCoordinator(enrichment::enrichTrack).run(selected.map { it.trackId })

        assertEquals(10, result.progress.total)
        assertEquals(10, result.progress.succeeded)
        assertEquals(0, result.progress.skipped)
        assertEquals(0, result.progress.failed)
        assertTrue(!result.progress.cancelled)

        val after = snapshot()
        assertEquals(before.tracks, after.tracks)
        assertEquals(before.locations, after.locations)
        assertEquals(before.bindings, after.bindings)
        assertEquals(beforeOverrides, database.trackMetadataDao().listOverridesForTracks(selected.map { it.trackId }))
        assertEquals(10, after.metadata)
        assertEquals(0, after.overrides)
        assertEquals(
            selected.map { it.trackId }.toSet(),
            database.trackMetadataDao().listAll().map { it.trackId }.toSet(),
        )
        assertEquals("ok", database.openHelper.readableDatabase.query("PRAGMA integrity_check").use {
            check(it.moveToFirst())
            it.getString(0)
        })
        assertEquals(0, database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").count)
    }

    private fun snapshot(): Snapshot = Snapshot(
        tracks = database.trackDao().listLocations().map { it.trackId }.toSet().size,
        locations = database.trackDao().listLocations().size,
        bindings = database.locatorBindingDao().listAll().size,
        metadata = database.trackMetadataDao().listAll().size,
        overrides = database.trackMetadataDao().listOverridesForTracks(
            database.trackDao().listLocations().map { it.trackId }.distinct(),
        ).size,
    )

    private fun anonymous(trackId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(trackId.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(12)

    private data class PreviewSelection(
        val trackId: String,
        val format: AudioFormat,
        val uri: Uri,
    )

    private data class Snapshot(
        val tracks: Int,
        val locations: Int,
        val bindings: Int,
        val metadata: Int,
        val overrides: Int,
    )

    private companion object {
        const val MAX_PER_FORMAT = 5
        val EXPECTED_HASHES = setOf(
            "df6350bbcdb1", "7fe6ebaa04f7", "324871a4c330", "07286acf889e", "974e2e03dfc4",
            "806612fa6641", "e30ce455c99f", "74fc51337de1", "88cf2758a4a4", "ad0ed506e2a6",
        )
    }
}
