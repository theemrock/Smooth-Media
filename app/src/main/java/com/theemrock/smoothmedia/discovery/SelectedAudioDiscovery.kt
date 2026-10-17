package com.theemrock.smoothmedia.discovery

import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.source.TreePermissionGateway
import com.theemrock.smoothmedia.source.TreePermissionInspection
import java.util.ArrayDeque

enum class AudioFormat {
    MP3,
    FLAC,
    ;

    companion object {
        fun fromDocument(displayName: String, mimeType: String?): AudioFormat? {
            val extensionFormat = when {
                displayName.endsWith(".mp3", ignoreCase = true) -> MP3
                displayName.endsWith(".flac", ignoreCase = true) -> FLAC
                else -> null
            }
            val mimeFormat = when {
                mimeType.equals("audio/mpeg", ignoreCase = true) ||
                    mimeType.equals("audio/mp3", ignoreCase = true) -> MP3

                mimeType.equals("audio/flac", ignoreCase = true) ||
                    mimeType.equals("audio/x-flac", ignoreCase = true) -> FLAC

                else -> null
            }
            return if (extensionFormat != null && mimeFormat != null && extensionFormat != mimeFormat) {
                null
            } else {
                extensionFormat ?: mimeFormat
            }
        }
    }
}

data class DiscoveredAudioFile(
    val sourceId: String,
    val uri: String,
    val documentId: String,
    val displayName: String,
    val format: AudioFormat,
    val mimeType: String?,
    val sizeBytes: Long?,
    val modifiedAtEpochMs: Long?,
)

data class TreeDocument(
    val documentId: String,
    val parentDocumentId: String? = null,
    val uri: String,
    val displayName: String,
    val mimeType: String?,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val modifiedAtEpochMs: Long?,
)

sealed interface DirectoryReadResult {
    data class Readable(
        val children: List<TreeDocument>,
        val unreadableEntryCount: Int = 0,
    ) : DirectoryReadResult

    data object PermissionLost : DirectoryReadResult

    data object Unavailable : DirectoryReadResult

    data object Unreadable : DirectoryReadResult

    data object Failed : DirectoryReadResult
}

enum class DocumentReadability {
    READABLE,
    PERMISSION_LOST,
    UNREADABLE,
}

interface SafDocumentTreeReader {
    fun listChildren(directoryUri: String): DirectoryReadResult

    fun checkReadable(fileUri: String): DocumentReadability
}

sealed interface AudioDiscoveryResult {
    data class Completed(
        val sourceId: String,
        val files: List<DiscoveredAudioFile>,
        val skippedUnreadableCount: Int,
    ) : AudioDiscoveryResult

    data class PermissionLost(val sourceId: String) : AudioDiscoveryResult

    data class SourceUnavailable(val sourceId: String) : AudioDiscoveryResult

    data class UnsupportedSource(val sourceId: String) : AudioDiscoveryResult

    data class NotSelected(val sourceId: String) : AudioDiscoveryResult

    data class InvalidSource(val sourceId: String) : AudioDiscoveryResult

    data class Failed(val sourceId: String) : AudioDiscoveryResult
}

/** Discovers supported audio under one explicitly registered SAF tree; it never writes to Room. */
class SelectedAudioDiscovery(
    private val permissions: TreePermissionGateway,
    private val treeReader: SafDocumentTreeReader,
    private val onDocument: (sourceId: String, document: TreeDocument) -> Unit = { _, _ -> },
) {
    fun discover(source: LibrarySourceEntity): AudioDiscoveryResult {
        val sourceId = source.sourceId
        if (source.selectionState != SourceSelectionState.ACTIVE) {
            return AudioDiscoveryResult.NotSelected(sourceId)
        }
        if (source.sourceType != LibrarySourceType.SAF_TREE) {
            return AudioDiscoveryResult.UnsupportedSource(sourceId)
        }

        val permission = try {
            permissions.inspect(source.rootUri)
        } catch (_: Exception) {
            return AudioDiscoveryResult.Failed(sourceId)
        }
        when (permission) {
            is TreePermissionInspection.Available -> Unit
            is TreePermissionInspection.Unavailable -> return AudioDiscoveryResult.SourceUnavailable(sourceId)
            TreePermissionInspection.PermissionLost -> return AudioDiscoveryResult.PermissionLost(sourceId)
            TreePermissionInspection.InvalidUri -> return AudioDiscoveryResult.InvalidSource(sourceId)
            TreePermissionInspection.Failed -> return AudioDiscoveryResult.Failed(sourceId)
        }

        val files = linkedMapOf<String, DiscoveredAudioFile>()
        val visitedDirectories = mutableSetOf<String>()
        var skippedUnreadable = 0

        fun currentSourceIssue(): AudioDiscoveryResult? = try {
            when (permissions.inspect(source.rootUri)) {
                is TreePermissionInspection.Available -> null
                is TreePermissionInspection.Unavailable -> AudioDiscoveryResult.SourceUnavailable(sourceId)
                TreePermissionInspection.PermissionLost -> AudioDiscoveryResult.PermissionLost(sourceId)
                TreePermissionInspection.InvalidUri -> AudioDiscoveryResult.InvalidSource(sourceId)
                TreePermissionInspection.Failed -> AudioDiscoveryResult.Failed(sourceId)
            }
        } catch (_: Exception) {
            AudioDiscoveryResult.Failed(sourceId)
        }

        val directories = ArrayDeque<Pair<String, Boolean>>()
        directories.addLast(source.rootUri to true)
        while (directories.isNotEmpty()) {
            val (directoryUri, isRoot) = directories.removeLast()
            if (!visitedDirectories.add(directoryUri)) continue
            val listing = try {
                treeReader.listChildren(directoryUri)
            } catch (_: Exception) {
                DirectoryReadResult.Failed
            }
            when (listing) {
                is DirectoryReadResult.Readable -> {
                    skippedUnreadable += listing.unreadableEntryCount
                    listing.children.forEach { document ->
                        onDocument(sourceId, document)
                        if (document.isDirectory) {
                            directories.addLast(document.uri to false)
                        } else {
                            val format = AudioFormat.fromDocument(document.displayName, document.mimeType)
                                ?: return@forEach
                            when (safeCheckReadable(document.uri)) {
                                DocumentReadability.READABLE -> files.putIfAbsent(
                                    document.documentId,
                                    DiscoveredAudioFile(
                                        sourceId = sourceId,
                                        uri = document.uri,
                                        documentId = document.documentId,
                                        displayName = document.displayName,
                                        format = format,
                                        mimeType = document.mimeType,
                                        sizeBytes = document.sizeBytes,
                                        modifiedAtEpochMs = document.modifiedAtEpochMs,
                                    ),
                                )

                                DocumentReadability.PERMISSION_LOST -> {
                                    currentSourceIssue()?.let { return it }
                                    skippedUnreadable++
                                }

                                DocumentReadability.UNREADABLE -> skippedUnreadable++
                            }
                        }
                    }
                }

                DirectoryReadResult.PermissionLost -> {
                    currentSourceIssue()?.let { return it }
                    if (isRoot) return AudioDiscoveryResult.SourceUnavailable(sourceId)
                    skippedUnreadable++
                }
                DirectoryReadResult.Unavailable -> if (isRoot) {
                    return AudioDiscoveryResult.SourceUnavailable(sourceId)
                } else {
                    currentSourceIssue()?.let { return it }
                    skippedUnreadable++
                }

                DirectoryReadResult.Unreadable -> if (isRoot) {
                    return AudioDiscoveryResult.Failed(sourceId)
                } else {
                    skippedUnreadable++
                }

                DirectoryReadResult.Failed -> if (isRoot) {
                    return AudioDiscoveryResult.Failed(sourceId)
                } else {
                    skippedUnreadable++
                }
            }
        }

        return AudioDiscoveryResult.Completed(sourceId, files.values.toList(), skippedUnreadable)
    }

    private fun safeCheckReadable(uri: String): DocumentReadability = try {
        treeReader.checkReadable(uri)
    } catch (_: SecurityException) {
        DocumentReadability.PERMISSION_LOST
    } catch (_: Exception) {
        DocumentReadability.UNREADABLE
    }
}
