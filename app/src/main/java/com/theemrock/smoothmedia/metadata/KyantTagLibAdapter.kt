package com.theemrock.smoothmedia.metadata

import android.os.ParcelFileDescriptor
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import java.util.HashMap

/** Kyant0/taglib bridge. The caller owns and closes the ParcelFileDescriptor. */
class KyantTagLibAdapter : FileTagAdapter {
    override fun read(descriptor: ParcelFileDescriptor): Result<FileTagSnapshot> = runCatching {
        val metadata = TagLib.getMetadata(nativeFd(descriptor), true)
            ?: error("TagLib returned no metadata")
        FileTagSnapshot(
            properties = metadata.propertyMap.mapValues { (_, values) -> values.toList() },
            pictures = TagLib.getPictures(nativeFd(descriptor)).map(::toPicture),
        )
    }

    override fun write(
        descriptor: ParcelFileDescriptor,
        change: FileTagChange,
    ): FileTagWriteResult {
        val nativeProperties = HashMap<String, Array<String>>(change.properties.size).apply {
            putAll(change.properties.mapValues { (_, values) -> values.toTypedArray() })
        }
        val writeResult = runCatching {
            check(TagLib.savePropertyMap(nativeFd(descriptor), nativeProperties)) {
                "TagLib property save failed"
            }
            change.pictures?.let { pictures ->
                check(TagLib.savePictures(nativeFd(descriptor), pictures.map(::toNativePicture).toTypedArray())) {
                    "TagLib picture save failed"
                }
            }
        }
        if (writeResult.isFailure) {
            return FileTagWriteResult.Failed(FileTagWriteFailure.WRITE_FAILED)
        }
        val readBack = read(descriptor).getOrNull()
            ?: return FileTagWriteResult.Failed(FileTagWriteFailure.READ_BACK_FAILED)
        return FileTagWriteResult.Written(readBack)
    }

    /**
     * Kyant0/taglib 1.0.5 consumes and closes the detached descriptor in its
     * native FileStream. The caller-owned descriptor remains open until its
     * surrounding use block closes it.
     */
    private fun nativeFd(descriptor: ParcelFileDescriptor): Int = descriptor.dup().detachFd()

    private fun toPicture(picture: Picture) = FileTagPicture(
        data = picture.data,
        mimeType = picture.mimeType,
        description = picture.description,
        pictureType = picture.pictureType,
    )

    private fun toNativePicture(picture: FileTagPicture) = Picture(
        data = picture.data,
        mimeType = picture.mimeType,
        description = picture.description,
        pictureType = picture.pictureType,
    )
}
