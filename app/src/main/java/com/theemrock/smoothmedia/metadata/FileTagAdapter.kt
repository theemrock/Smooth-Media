package com.theemrock.smoothmedia.metadata

import android.os.ParcelFileDescriptor

data class FileTagPicture(
    val data: ByteArray,
    val mimeType: String,
    val description: String,
    val pictureType: String,
) {
    override fun equals(other: Any?): Boolean = other is FileTagPicture &&
        data.contentEquals(other.data) &&
        mimeType == other.mimeType &&
        description == other.description &&
        pictureType == other.pictureType

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + pictureType.hashCode()
        return result
    }
}

data class FileTagSnapshot(
    val properties: Map<String, List<String>>,
    val pictures: List<FileTagPicture>,
)

data class FileTagChange(
    val properties: Map<String, List<String>>,
    val pictures: List<FileTagPicture>? = null,
)

sealed interface FileTagWriteResult {
    data class Written(val snapshot: FileTagSnapshot) : FileTagWriteResult
    data class Failed(val reason: FileTagWriteFailure) : FileTagWriteResult
}

enum class FileTagWriteFailure {
    UNSUPPORTED_FORMAT,
    DESCRIPTOR_NOT_SEEKABLE,
    READ_FAILED,
    WRITE_FAILED,
    READ_BACK_FAILED,
}

interface FileTagAdapter {
    fun read(descriptor: ParcelFileDescriptor): Result<FileTagSnapshot>

    fun write(
        descriptor: ParcelFileDescriptor,
        change: FileTagChange,
    ): FileTagWriteResult
}
