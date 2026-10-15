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
            val raw = RawAudioMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
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
}
