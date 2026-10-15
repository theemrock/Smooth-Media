package com.theemrock.smoothmedia.metadata

import android.database.sqlite.SQLiteDatabase
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Read-only P01-09P diagnosis. It never constructs or calls the enrichment use-case.
 * The database is opened read-only so this test cannot create a metadata write.
 */
@RunWith(AndroidJUnit4::class)
class EightMissingTracksReadOnlyDiagnosisTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var database: SQLiteDatabase

    @Before
    fun openProductionDatabaseReadOnly() {
        val file = context.getDatabasePath("smooth-media-catalog.db")
        database = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    @After
    fun closeProductionDatabase() {
        database.close()
    }

    @Test
    fun diagnosesExactlyP0109OMissingTracksWithoutWrites() {
        val rows = database.rawQuery(
            """
            SELECT t.track_id, l.location_id, l.source_id, l.format, l.availability,
                   b.locator
            FROM tracks t
            LEFT JOIN track_metadata m ON m.trackId = t.track_id
            JOIN track_locations l ON l.track_id = t.track_id
            LEFT JOIN locator_bindings b ON b.location_id = l.location_id
            WHERE m.trackId IS NULL
            ORDER BY t.track_id
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DiagnosisRow(
                            trackId = cursor.getString(0),
                            locationId = cursor.getString(1),
                            sourceId = cursor.getString(2),
                            format = cursor.getString(3),
                            availability = cursor.getString(4),
                            locator = cursor.getStringOrNull(5),
                        ),
                    )
                }
            }
        }

        check(rows.size == EXPECTED_PREFIXES.size) { "Expected exactly 8 missing Tracks, got ${rows.size}" }
        check(rows.all { row -> EXPECTED_PREFIXES.any { row.trackId.startsWith(it) } }) {
            "Missing Track set differs from P01-09O prefixes"
        }
        check(rows.map { it.trackId }.distinct().size == rows.size) { "Duplicate Track IDs in diagnosis set" }

        val reader = AndroidAudioMetadataReader(context)
        val normalizer = MetadataNormalizer()
        rows.forEach { row ->
            val bindingPresent = row.locator != null
            val excluded = row.locator != null && exists(
                "SELECT 1 FROM catalog_exclusions WHERE source_id = ? AND locator = ? LIMIT 1",
                arrayOf(row.sourceId, row.locator),
            )
            val redactedName = row.locator?.let(::documentName) ?: "UNKNOWN"
            val uri = row.locator?.let(Uri::parse)
            val saf = if (uri == null) SafResult("FAIL", "MISSING_BINDING", "UNKNOWN") else openAndRead(uri)
            val readResult = if (uri == null) null else reader.read(uri)
            val normalizerResult = when (readResult) {
                is MetadataReadResult.Success -> {
                    check(normalizer.normalize(readResult.raw) == readResult.metadata) {
                        "Normalizer mismatch for ${row.trackId.take(14)}"
                    }
                    "PASS"
                }
                is MetadataReadResult.Failed -> "NOT_RUN(${readResult.reason})"
                null -> "NOT_RUN(NO_URI)"
            }
            val readerLabel = when (readResult) {
                is MetadataReadResult.Success -> "PASS"
                is MetadataReadResult.Failed -> "FAIL(${readResult.reason})"
                null -> "NOT_RUN"
            }
            val readerException = if (readResult is MetadataReadResult.Failed && uri != null) {
                platformReaderException(uri)
            } else {
                "NONE"
            }
            val preWrite = if (row.availability == "AVAILABLE" && bindingPresent && !excluded &&
                saf.open == "PASS" && saf.read == "PASS" && readResult is MetadataReadResult.Success
            ) "PASS_WRITE_ELIGIBLE_ONLY" else "FAIL_OR_NOT_ELIGIBLE"
            val fields = (readResult as? MetadataReadResult.Success)?.metadata?.let(::fieldSummary) ?: "UNKNOWN"
            Log.i(
                TAG,
                "TRACK|id=${row.trackId.take(14)}|format=${row.format}|location=${row.locationId.take(14)}|" +
                    "documentName=$redactedName|locationAvailability=${row.availability}|binding=$bindingPresent|" +
                    "excluded=$excluded|safOpen=${saf.open}|safRead=${saf.read}|safException=${saf.exceptionType}|" +
                    "reader=$readerLabel|readerException=$readerException|normalizer=$normalizerResult|" +
                    "preWrite=$preWrite|fields=$fields",
            )
        }
    }

    private fun platformReaderException(uri: Uri): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            "NONE"
        } catch (error: Exception) {
            error::class.java.name
        } finally {
            retriever.close()
        }
    }

    private fun openAndRead(uri: Uri): SafResult {
        return try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream == null) {
                SafResult("FAIL", "FAIL", "NULL_STREAM")
            } else {
                stream.use { input ->
                    val buffer = ByteArray(1)
                    input.read(buffer)
                }
                SafResult("PASS", "PASS", "NONE")
            }
        } catch (error: Exception) {
            SafResult("FAIL", "FAIL", error::class.java.simpleName)
        }
    }

    private fun exists(sql: String, args: Array<String>): Boolean = database.rawQuery(sql, args).use { it.moveToFirst() }

    private fun documentName(locator: String): String = Uri.parse(locator).lastPathSegment
        ?.substringAfterLast('/')
        ?.let(Uri::decode)
        ?.takeIf { it.isNotBlank() }
        ?: "UNKNOWN"

    private fun fieldSummary(metadata: NormalizedAudioMetadata): String = listOf(
        "title" to metadata.title.status,
        "artist" to metadata.artist.status,
        "album" to metadata.album.status,
        "albumArtist" to metadata.albumArtist.status,
        "genre" to metadata.genres.status,
        "track" to metadata.trackNumber.status,
        "disc" to metadata.discNumber.status,
        "year" to metadata.year.status,
        "duration" to metadata.durationMs.status,
    ).joinToString(",") { (name, status) -> "$name=$status" }

    private data class DiagnosisRow(
        val trackId: String,
        val locationId: String,
        val sourceId: String,
        val format: String,
        val availability: String,
        val locator: String?,
    )

    private data class SafResult(val open: String, val read: String, val exceptionType: String)

    private fun android.database.Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private companion object {
        const val TAG = "SmoothMediaP01_09P"
        val EXPECTED_PREFIXES = setOf(
            "track-195bfa", "track-3042c1", "track-55a540", "track-6d830f",
            "track-744715", "track-8892ee", "track-b60cd1", "track-c98a34",
        )
    }
}
