package com.theemrock.smoothmedia.metadata

import com.theemrock.smoothmedia.catalog.database.dao.TrackMetadataDao
import com.theemrock.smoothmedia.genre.RoomGenreRepository

/** User-started, idempotent materialization of stored Room metadata genres. */
class GenreMetadataBackfill(
    private val metadataDao: TrackMetadataDao,
    private val genreRepository: RoomGenreRepository,
) {
    data class Result(
        val processedTracks: Int,
        val createdAssignments: Int,
    )

    fun run(): Result {
        var processedTracks = 0
        var materializedGenres = 0
        metadataDao.listActiveMetadata().forEach { metadata ->
            val genres = metadata.normalizedGenres
                ?.split(GENRE_SEPARATOR)
                .orEmpty()
                .map(String::trim)
                .filter(String::isNotEmpty)
            if (genres.isEmpty()) return@forEach
            genreRepository.syncMetadataGenres(metadata.trackId, genres)
            processedTracks += 1
            materializedGenres += genres.distinctBy(String::lowercase).size
        }
        return Result(processedTracks, materializedGenres)
    }

    private companion object {
        const val GENRE_SEPARATOR = "\u001F"
    }
}
