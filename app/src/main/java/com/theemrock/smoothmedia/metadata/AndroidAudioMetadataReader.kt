package com.theemrock.smoothmedia.metadata

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

/** Reads embedded MP3/FLAC metadata through the platform API without writing to the source file. */
class AndroidAudioMetadataReader(
    private val context: Context,
    private val normalizer: MetadataNormalizer = MetadataNormalizer(),
) : AudioMetadataReader {
    override fun read(uri: Uri): MetadataReadResult {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            // The platform guesses the text encoding of ID3 frames and turns e.g. latin1 "Yavuz Çetin" into CJK;
            // TagLib (the same reader the tag editor uses) decodes them correctly, so its text wins when present.
            val tags = tagLibText(uri)
            val raw = RawAudioMetadata(
                title = tags["TITLE"] ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = tags["ARTIST"] ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = tags["ALBUM"] ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = tags["ALBUMARTIST"]
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                genre = tags["GENRE"] ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                trackNumber = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER,
                ),
                discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION),
            )
            MetadataReadResult.Success(
                raw = raw,
                metadata = normalizer.normalize(raw),
            )
        } catch (_: SecurityException) {
            MetadataReadResult.Failed(MetadataReadFailure.PERMISSION_DENIED)
        } catch (_: IllegalArgumentException) {
            MetadataReadResult.Failed(MetadataReadFailure.SOURCE_UNAVAILABLE)
        } catch (_: RuntimeException) {
            MetadataReadResult.Failed(MetadataReadFailure.READ_FAILED)
        } finally {
            retriever.close()
        }
    }

    /** Non-blank TagLib text properties joined with ";", empty when TagLib cannot read the file. */
    private fun tagLibText(uri: Uri): Map<String, String> = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            KyantTagLibAdapter().read(descriptor).getOrNull()?.properties.orEmpty()
                .mapValues { (_, values) -> values.filter { it.isNotBlank() }.joinToString(";") }
                .filterValues { it.isNotEmpty() }
        }
    }.getOrNull().orEmpty()
}
