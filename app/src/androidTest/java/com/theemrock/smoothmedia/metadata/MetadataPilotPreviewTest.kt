package com.theemrock.smoothmedia.metadata

import android.net.Uri
import android.util.Log
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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetadataPilotPreviewTest {
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
    fun previewsUpToFiveTracksPerFormatWithoutWriting() {
        val before = snapshot()
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

        val locations = database.trackDao().listLocations()
            .asSequence()
            .filter { it.sourceId == source!!.sourceId && it.availability == LocationAvailability.AVAILABLE }
            .filter { it.format == AudioFormat.MP3 || it.format == AudioFormat.FLAC }
            .mapNotNull { location ->
                val binding = database.locatorBindingDao().findByLocation(location.locationId)
                    ?: return@mapNotNull null
                if (database.catalogExclusionDao().isLocatorExcluded(location.sourceId, binding.locator)) {
                    return@mapNotNull null
                }
                PreviewCandidate(location.trackId, location.format, Uri.parse(binding.locator))
            }
            .distinctBy { it.trackId }
            .toList()

        val selected = locations.groupBy { it.format }
            .flatMap { (_, candidates) -> candidates.take(MAX_PER_FORMAT) }
        assumeTrue("No eligible SAF MP3/FLAC locations", selected.isNotEmpty())

        val reader = AndroidAudioMetadataReader(context)
        selected.forEach { candidate ->
            when (val result = reader.read(candidate.uri)) {
                is MetadataReadResult.Success -> Log.i(
                    TAG,
                    "PREVIEW|format=${candidate.format}|track=${anonymous(candidate.trackId)}|" +
                        "title=${field(result.metadata.title)}|artist=${field(result.metadata.artist)}|" +
                        "album=${field(result.metadata.album)}|albumArtist=${field(result.metadata.albumArtist)}|" +
                        "genre=${field(result.metadata.genres)}|trackNo=${field(result.metadata.trackNumber)}|" +
                        "discNo=${field(result.metadata.discNumber)}|year=${field(result.metadata.year)}|" +
                        "durationMs=${field(result.metadata.durationMs)}",
                )

                is MetadataReadResult.Failed -> Log.i(
                    TAG,
                    "PREVIEW|format=${candidate.format}|track=${anonymous(candidate.trackId)}|" +
                        "ERROR:${result.reason}",
                )
            }
        }

        val after = snapshot()
        check(before == after) { "Metadata preview changed catalog: before=$before after=$after" }
        Log.i(
            TAG,
            "SUMMARY|mp3=${selected.count { it.format == AudioFormat.MP3 }}|" +
                "flac=${selected.count { it.format == AudioFormat.FLAC }}|" +
                "writes=0|catalogUnchanged=true",
        )
    }

    private fun snapshot(): CatalogSnapshot = CatalogSnapshot(
        tracks = database.trackDao().listLocations().map { it.trackId }.toSet().size,
        locations = database.trackDao().listLocations().size,
        bindings = database.locatorBindingDao().listAll().size,
        metadata = scalar("SELECT COUNT(*) FROM track_metadata"),
        overrides = scalar("SELECT COUNT(*) FROM track_metadata_overrides"),
    )

    private fun scalar(sql: String): Int = database.openHelper.readableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst()) { "No scalar result for $sql" }
        cursor.getInt(0)
    }

    private fun <T> field(field: MetadataField<T>): String =
        "${field.status}:${field.value ?: "<missing>"}"

    private fun anonymous(trackId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(trackId.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(12)

    private data class PreviewCandidate(
        val trackId: String,
        val format: AudioFormat,
        val uri: Uri,
    )

    private data class CatalogSnapshot(
        val tracks: Int,
        val locations: Int,
        val bindings: Int,
        val metadata: Int,
        val overrides: Int,
    )

    private companion object {
        const val MAX_PER_FORMAT = 5
        const val TAG = "SmoothMediaP01_09G"
    }
}
