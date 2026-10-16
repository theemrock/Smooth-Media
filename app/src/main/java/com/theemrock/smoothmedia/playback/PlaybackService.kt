package com.theemrock.smoothmedia.playback

import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.theemrock.smoothmedia.library.LibraryCollectionRow
import com.theemrock.smoothmedia.library.LibraryTrackRow
import com.theemrock.smoothmedia.source.SourceRegistryFactory

/** Single process-wide Media3 session owner. */
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private var engine: Media3PlaybackEngine? = null
    private var runtime: com.theemrock.smoothmedia.source.SourceRegistryRuntime? = null

    override fun onCreate() {
        super.onCreate()
        runtime = SourceRegistryFactory.create(this)
        val resolver = CatalogPlaybackLocationResolver(runtime!!.database)
        engine = Media3PlaybackEngine(this, resolver::resolve)
        PlaybackEngineRegistry.engine = engine
        val libraryDao = runtime!!.database.libraryDao()
        session = engine!!.onPlayerThreadBlocking {
            MediaLibrarySession.Builder(
                this,
                engine!!.player,
                object : MediaLibrarySession.Callback {
                    override fun onGetLibraryRoot(
                        session: MediaLibrarySession,
                        browser: MediaSession.ControllerInfo,
                        params: MediaLibraryService.LibraryParams?,
                    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
                        LibraryResult.ofItem(folderItem(ROOT, "SMOOTH MEDIA"), params),
                    )

                    override fun onGetChildren(
                        session: MediaLibrarySession,
                        browser: MediaSession.ControllerInfo,
                        parentId: String,
                        page: Int,
                        pageSize: Int,
                        params: MediaLibraryService.LibraryParams?,
                    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                        val limit = pageSize.coerceIn(1, 100)
                        val offset = page.coerceAtLeast(0) * limit
                        val items = runCatching {
                            when (parentId) {
                                ROOT -> listOf(
                                    folderItem(SONGS, "Songs"), folderItem(ALBUMS, "Albums"),
                                    folderItem(ARTISTS, "Artists"), folderItem(GENRES, "Genres"),
                                    folderItem(FOLDERS, "Folders"),
                                )
                                SONGS -> libraryDao.loadTracks(offset, limit).map(::trackItem)
                                ALBUMS -> libraryDao.loadAlbums(offset, limit).map { collectionItem(ALBUMS, it) }
                                ARTISTS -> libraryDao.loadArtists(offset, limit).map { collectionItem(ARTISTS, it) }
                                GENRES -> libraryDao.loadGenres(offset, limit).map { collectionItem(GENRES, it) }
                                FOLDERS -> libraryDao.loadFolders(offset, limit).map { collectionItem(FOLDERS, it) }
                                else -> emptyList()
                            }
                        }.getOrDefault(emptyList())
                        return Futures.immediateFuture(LibraryResult.ofItemList(items, params))
                    }
                },
            ).build()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        engine?.close()
        PlaybackEngineRegistry.engine = null
        engine = null
        runtime?.close()
        runtime = null
        super.onDestroy()
    }

    private fun folderItem(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build())
        .build()

    private fun trackItem(row: LibraryTrackRow): MediaItem = MediaItem.Builder()
        .setMediaId(row.trackId)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(row.title ?: "Unknown title").setArtist(row.artist).setAlbumTitle(row.album).setDurationMs(row.durationMs).setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build())
        .build()

    private fun collectionItem(category: String, row: LibraryCollectionRow): MediaItem = MediaItem.Builder()
        .setMediaId("$category:${row.key}")
        .setMediaMetadata(MediaMetadata.Builder().setTitle(row.title).setSubtitle(row.subtitle).setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build())
        .build()

    private companion object {
        const val ROOT = "root"
        const val SONGS = "songs"
        const val ALBUMS = "albums"
        const val ARTISTS = "artists"
        const val GENRES = "genres"
        const val FOLDERS = "folders"
    }
}

object PlaybackEngineRegistry {
    @Volatile
    var engine: PlaybackEngine? = null
}
