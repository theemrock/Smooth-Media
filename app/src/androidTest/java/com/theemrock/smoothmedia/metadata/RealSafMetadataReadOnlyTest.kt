package com.theemrock.smoothmedia.metadata

import android.net.Uri
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.CatalogDatabaseFactory
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealSafMetadataReadOnlyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var database: CatalogDatabase

    @Before
    fun openCatalogReadOnly() {
        database = CatalogDatabaseFactory.create(context)
    }

    @After
    fun closeCatalog() {
        database.close()
    }

    @Test
    fun readsAtMostOneMp3AndFlacWithoutCatalogWrites() {
        val before = snapshot()
        val sources = database.librarySourceDao().listAll().filter {
            it.sourceType == LibrarySourceType.SAF_TREE &&
                it.selectionState == SourceSelectionState.ACTIVE &&
                it.permissionState == SourcePermissionState.GRANTED &&
                it.availability == SourceAvailability.AVAILABLE
        }
        val persisted = context.contentResolver.persistedUriPermissions
        val source = sources.firstOrNull { candidate ->
            persisted.any { permission ->
                permission.isReadPermission &&
                    (permission.uri == Uri.parse(candidate.rootUri) ||
                        Uri.parse(candidate.rootUri).toString().startsWith(permission.uri.toString()))
            }
        }
        assumeTrue("No active SAF source with persisted read permission", source != null)

        val locations = database.trackDao().listLocations()
            .filter { it.sourceId == source!!.sourceId && it.availability.name == "AVAILABLE" }
            .mapNotNull { location ->
                val binding = database.locatorBindingDao().findByLocation(location.locationId)
                    ?: return@mapNotNull null
                val format = location.format
                if (format != AudioFormat.MP3 && format != AudioFormat.FLAC) return@mapNotNull null
                format to Uri.parse(binding.locator)
            }
        val selected = listOf(AudioFormat.MP3, AudioFormat.FLAC).mapNotNull { format ->
            locations.firstOrNull { it.first == format }
        }
        assumeTrue("No readable catalog SAF MP3/FLAC fixture", selected.isNotEmpty())

        val reader = AndroidAudioMetadataReader(context)
        selected.forEach { (format, uri) ->
            val result = reader.read(uri)
            when (result) {
                is MetadataReadResult.Success -> Log.i(
                    TAG,
                    "READ|format=$format|uri=$uri|" +
                        "title=${field(result.metadata.title)}|artist=${field(result.metadata.artist)}|" +
                        "album=${field(result.metadata.album)}|albumArtist=${field(result.metadata.albumArtist)}|" +
                        "genre=${field(result.metadata.genres)}|track=${field(result.metadata.trackNumber)}|" +
                        "disc=${field(result.metadata.discNumber)}|year=${field(result.metadata.year)}|" +
                        "durationMs=${field(result.metadata.durationMs)}",
                )

                is MetadataReadResult.Failed -> Log.i(
                    TAG,
                    "READ_FAILED|format=$format|uri=$uri|reason=${result.reason}",
                )
            }
        }

        val after = snapshot()
        check(before == after) { "Read-only pilot changed catalog: before=$before after=$after" }
    }

    private fun snapshot(): CatalogSnapshot = CatalogSnapshot(
        tracks = database.trackDao().listLocations().map { it.trackId }.toSet().size,
        locations = database.trackDao().listLocations().size,
        bindings = database.locatorBindingDao().listAll().size,
        metadata = scalar("SELECT COUNT(*) FROM track_metadata"),
        overrides = scalar("SELECT COUNT(*) FROM track_metadata_overrides"),
    )

    private fun scalar(sql: String): Int = database.openHelper.readableDatabase
        .query(sql)
        .use { cursor ->
            check(cursor.moveToFirst()) { "No scalar result for $sql" }
            cursor.getInt(0)
        }

    private fun <T> field(field: MetadataField<T>): String =
        "${field.status}:${field.value ?: "<missing>"}"

    private data class CatalogSnapshot(
        val tracks: Int,
        val locations: Int,
        val bindings: Int,
        val metadata: Int,
        val overrides: Int,
    )

    private companion object {
        const val TAG = "SmoothMediaP01_09E"
    }
}
