package com.theemrock.smoothmedia.metadata

import android.database.sqlite.SQLiteDatabase
import android.content.res.AssetFileDescriptor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** P01-09Q: stage-level, read-only localization of the platform retriever failure. */
@RunWith(AndroidJUnit4::class)
class EightMissingTracksRetrieverStageDiagnosisTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var database: SQLiteDatabase

    @Before
    fun openProductionDatabaseReadOnly() {
        database = SQLiteDatabase.openDatabase(
            context.getDatabasePath("smooth-media-catalog.db").path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
    }

    @After
    fun closeProductionDatabase() {
        database.close()
    }

    @Test
    fun localizesRetrieverStageForExactlyP0109PMissingTracks() {
        val rows = database.rawQuery(
            """
            SELECT t.track_id, l.location_id, l.format, l.availability, b.locator
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
                        Row(
                            trackId = cursor.getString(0),
                            locationId = cursor.getString(1),
                            format = cursor.getString(2),
                            availability = cursor.getString(3),
                            locator = if (cursor.isNull(4)) null else cursor.getString(4),
                        ),
                    )
                }
            }
        }
        check(rows.size == PREFIXES.size) { "Expected 8 missing Tracks, got ${rows.size}" }
        check(rows.all { row -> PREFIXES.any(row.trackId::startsWith) }) {
            "P01-09O Track set changed"
        }

        rows.forEach(::diagnose)
    }

    private fun diagnose(row: Row) {
        val uri = row.locator?.let(Uri::parse)
        var descriptor: ParcelFileDescriptor? = null
        var retriever: MediaMetadataRetriever? = null
        val stages = linkedMapOf<String, String>()

        if (uri == null) {
            stages["openFileDescriptor"] = "FAIL|IllegalStateException|no-binding"
            stages["release"] = "NOT_RUN|no-retriever"
            log(row, stages)
            return
        }

        try {
            descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            if (descriptor == null) {
                stages["openFileDescriptor"] = "FAIL|NULL|provider-returned-null"
                stages["release"] = "NOT_RUN|no-descriptor"
                log(row, stages)
                return
            }
            stages["openFileDescriptor"] = "PASS|NONE|opened-read-only"
            stages["descriptorStatSize"] = "PASS|NONE|${descriptor.statSize}"
        } catch (error: Exception) {
            stages["openFileDescriptor"] = failure(error)
            stages["release"] = "NOT_RUN|no-descriptor"
            log(row, stages)
            return
        }

        try {
            val asset = context.contentResolver.openAssetFileDescriptor(uri, "r")
            if (asset == null) {
                stages["openAssetFileDescriptor"] = "FAIL|NULL|provider-returned-null"
            } else {
                asset.use { afd: AssetFileDescriptor ->
                    stages["openAssetFileDescriptor"] =
                        "PASS|NONE|startOffset=${afd.startOffset},length=${afd.length}"
                }
            }
        } catch (error: Exception) {
            stages["openAssetFileDescriptor"] = failure(error)
        }

        try {
            retriever = MediaMetadataRetriever()
            stages["createRetriever"] = "PASS|NONE|created"
        } catch (error: Exception) {
            stages["createRetriever"] = failure(error)
            stages["release"] = "NOT_RUN|no-retriever"
            descriptor.close()
            log(row, stages)
            return
        }

        val fileDescriptor = descriptor.fileDescriptor
        try {
            retriever.setDataSource(fileDescriptor)
            stages["setDataSourceFd"] = "PASS|NONE|default-offset-length"
        } catch (error: Exception) {
            stages["setDataSourceFd"] = failure(error)
        }

        try {
            val size = descriptor.statSize
            val length = if (size > 0L) size else Long.MAX_VALUE
            retriever.setDataSource(fileDescriptor, 0L, length)
            stages["setDataSourceFdOffsetLength"] = "PASS|NONE|offset=0,length=$length"
        } catch (error: Exception) {
            stages["setDataSourceFdOffsetLength"] = failure(error)
        }

        var uriDataSourceReady = false
        try {
            retriever.setDataSource(context, uri)
            stages["setDataSourceUri"] = "PASS|NONE|context-uri"
            uriDataSourceReady = true
        } catch (error: Exception) {
            stages["setDataSourceUri"] = failure(error)
        }

        EXTRACT_KEYS.forEach { (name, key) ->
            if (!uriDataSourceReady) {
                stages["extract_$name"] = "NOT_RUN|NO_DATA_SOURCE|source-setup-failed"
            } else {
                try {
                    val value = retriever.extractMetadata(key)
                    stages["extract_$name"] = if (value == null) {
                        "PASS|NONE|MISSING"
                    } else {
                        "PASS|NONE|AVAILABLE"
                    }
                } catch (error: Exception) {
                    stages["extract_$name"] = failure(error)
                }
            }
        }

        try {
            retriever.release()
            stages["release"] = "PASS|NONE|released"
        } catch (error: Exception) {
            stages["release"] = failure(error)
        }
        try {
            descriptor.close()
            stages["descriptorClose"] = "PASS|NONE|closed"
        } catch (error: Exception) {
            stages["descriptorClose"] = failure(error)
        }
        log(row, stages)
    }

    private fun log(row: Row, stages: Map<String, String>) {
        Log.i(
            TAG,
            "TRACK|id=${row.trackId.take(14)}|format=${row.format}|location=${row.locationId.take(14)}|" +
                "availability=${row.availability}|" +
                stages.entries.joinToString("|") { (stage, result) -> "$stage=$result" },
        )
    }

    private fun failure(error: Exception): String =
        "FAIL|${error::class.java.name}|${causeTypes(error)}|${safeMessage(error)}"

    private fun causeTypes(error: Throwable): String = generateSequence(error.cause) { it.cause }
        .joinToString(",") { it::class.java.name }
        .ifBlank { "NONE" }

    private fun safeMessage(error: Throwable): String {
        val message = error.message?.lowercase().orEmpty()
        return when {
            message.isBlank() -> "no-message"
            "permission" in message || "denied" in message -> "permission-related"
            "data source" in message || "datasource" in message -> "data-source-related"
            "offset" in message || "length" in message -> "descriptor-range-related"
            else -> "platform-retriever-error"
        }
    }

    private data class Row(
        val trackId: String,
        val locationId: String,
        val format: String,
        val availability: String,
        val locator: String?,
    )

    private companion object {
        const val TAG = "SmoothMediaP01_09Q"
        val PREFIXES = setOf(
            "track-195bfa", "track-3042c1", "track-55a540", "track-6d830f",
            "track-744715", "track-8892ee", "track-b60cd1", "track-c98a34",
        )
        val EXTRACT_KEYS = listOf(
            "title" to MediaMetadataRetriever.METADATA_KEY_TITLE,
            "artist" to MediaMetadataRetriever.METADATA_KEY_ARTIST,
            "album" to MediaMetadataRetriever.METADATA_KEY_ALBUM,
            "albumArtist" to MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST,
            "genre" to MediaMetadataRetriever.METADATA_KEY_GENRE,
            "trackNumber" to MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER,
            "discNumber" to MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER,
            "year" to MediaMetadataRetriever.METADATA_KEY_DATE,
            "duration" to MediaMetadataRetriever.METADATA_KEY_DURATION,
        )
    }
}
