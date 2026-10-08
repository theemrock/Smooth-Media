package com.theemrock.smoothmedia.p01x

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore

class MediaStoreMusicDiscovery(
    private val contentResolver: ContentResolver,
) {
    fun discover(): List<DiscoveredAudio> {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
        )
        val selection =
            "(${MediaStore.Audio.Media.RELATIVE_PATH} = ? OR " +
                "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?)"
        val selectionArgs = arrayOf("Music/", "Music/%")
        val result = mutableListOf<DiscoveredAudio>()

        contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameColumn) ?: continue
                val relativePath = cursor.getString(pathColumn) ?: continue
                val mimeType = cursor.getString(mimeColumn)
                if (!MusicProbePolicy.isMusicPath(relativePath) ||
                    !MusicProbePolicy.isSupportedAudio(mimeType, displayName)
                ) {
                    continue
                }

                val id = cursor.getLong(idColumn)
                result += DiscoveredAudio(
                    mediaStoreId = id,
                    contentUri = ContentUris.withAppendedId(collection, id),
                    displayName = displayName,
                    relativePath = relativePath,
                    mimeType = mimeType,
                    sizeBytes = cursor.getLong(sizeColumn),
                )
            }
        }

        return result
    }
}
