package com.theemrock.smoothmedia.metadata

import android.content.ContentResolver
import android.net.Uri
import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import java.util.concurrent.atomic.AtomicBoolean

data class MetadataEditorLoadResult(
    val items: List<MetadataEditorBatchItem>,
    val skippedTrackIds: List<String>,
)

class MetadataEditorCoordinator(
    private val database: CatalogDatabase,
    private val contentResolver: ContentResolver,
    private val adapter: FileTagAdapter = KyantTagLibAdapter(),
    private val hashProvider: SafMetadataHashProvider = SafMetadataHashProvider(contentResolver),
) {
    fun load(trackIds: List<String>): MetadataEditorLoadResult {
        val items = mutableListOf<MetadataEditorBatchItem>()
        val skipped = mutableListOf<String>()
        trackIds.distinct().forEach { trackId ->
            val locations = database.trackDao().findLocations(trackId)
                .filter { it.format == AudioFormat.MP3 || it.format == AudioFormat.FLAC }
            var loaded = false
            locations.forEach { location ->
                val binding = database.locatorBindingDao().findByLocation(location.locationId) ?: return@forEach
                val source = database.librarySourceDao().find(location.sourceId) ?: return@forEach
                val target = MetadataFileWriteTarget(
                    trackId = trackId,
                    locationId = location.locationId,
                    sourceId = location.sourceId,
                    locator = binding.locator,
                    format = location.format,
                    locationAvailability = location.availability,
                    sourceAvailability = source.availability,
                    sourcePermission = source.permissionState,
                    sourceSelection = source.selectionState,
                    excluded = database.catalogExclusionDao().isExcluded(
                        sourceId = location.sourceId,
                        locator = binding.locator,
                        algorithmName = location.currentFileHash.algorithmName,
                        algorithmVersion = location.currentFileHash.algorithmVersion,
                        digest = location.currentFileHash.digest,
                    ),
                    currentHash = location.currentFileHash,
                )
                val snapshot = runCatching {
                    contentResolver.openFileDescriptor(Uri.parse(binding.locator), "r")?.use { descriptor ->
                        adapter.read(descriptor).getOrThrow()
                    }
                }.getOrNull() ?: return@forEach
                items += MetadataEditorBatchItem(location.locationId, target, snapshot)
                loaded = true
            }
            if (!loaded) skipped += trackId
        }
        return MetadataEditorLoadResult(items.take(100), skipped)
    }

    fun save(
        items: List<MetadataEditorBatchItem>,
        edit: FileTagEdit,
        cancelled: AtomicBoolean,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): MetadataEditorBatchResult {
        val session = MetadataFileWriteSession(contentResolver, adapter) { locator -> hashProvider(locator) }
        val reconciler = MetadataFileWriteReconciler(database)
        var processed = 0
        return MetadataEditorBatchRunner().run(items, edit, cancelled::get) { item, requestedEdit ->
            val target = item.target
            val outcome = if (target == null) {
                MetadataFileWriteOutcome.Failed(FileTagWriteFailure.READ_FAILED)
            } else {
                session.write(target, FileTagEditMapper.toChange(item.snapshot, requestedEdit))
            }
            val accepted = target != null && outcome is MetadataFileWriteOutcome.Written && reconciler.reconcile(
                target = target,
                raw = outcome.snapshot.toRawAudioMetadata(),
                newHash = outcome.currentHash,
                nowEpochMs = System.currentTimeMillis(),
            )
            processed++
            onProgress(processed, items.size.coerceAtMost(100))
            if (accepted) MetadataEditorItemResult.SUCCESS else MetadataEditorItemResult.FAILURE
        }
    }
}

private fun FileTagSnapshot.toRawAudioMetadata() = RawAudioMetadata(
    title = properties["TITLE"]?.firstOrNull(),
    artist = properties["ARTIST"]?.firstOrNull(),
    album = properties["ALBUM"]?.firstOrNull(),
    albumArtist = properties["ALBUMARTIST"]?.firstOrNull(),
    genre = properties["GENRE"]?.joinToString(", "),
    trackNumber = properties["TRACKNUMBER"]?.firstOrNull(),
    discNumber = properties["DISCNUMBER"]?.firstOrNull(),
    year = properties["DATE"]?.firstOrNull(),
)
