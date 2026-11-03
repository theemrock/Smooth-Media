package com.theemrock.smoothmedia.library

/**
 * Rule tree for Smart Playlists (stage 1, rule-based, deterministic).
 *
 * Genre matching follows the agreed semantics: a track matches a genre when ANY of its
 * assignments (Primary or Secondary) is that genre, and by default descendants of the
 * genre count too. A track can match several genre rules; overlap is allowed.
 */
sealed interface SmartRule {
    /** AND over children. An empty group matches everything. */
    data class All(val children: List<SmartRule>) : SmartRule

    /** OR over children. An empty group matches nothing. */
    data class Any(val children: List<SmartRule>) : SmartRule

    data class Not(val child: SmartRule) : SmartRule

    data class Genre(val genreId: String, val includeDescendants: Boolean = true) : SmartRule

    /** Tracks with no genre assignment at all. */
    data object NoGenre : SmartRule

    data class ArtistIs(val artist: String) : SmartRule

    data class AlbumIs(val album: String) : SmartRule

    /** Case-insensitive substring match on the effective title (override wins). */
    data class TitleContains(val text: String) : SmartRule
}

/** Row of the root-genre query (Room maps the column aliases onto these fields). */
data class GenreRootRow(val genreId: String, val displayName: String)

/** One automatic list: a root genre, or the "no genre" list. Overlap between lists is allowed. */
data class AutoGenreList(
    val key: String,
    val title: String,
    val trackCount: Int,
    val rule: SmartRule,
)

/** A rule compiled to a boolean SQL expression over the alias `t` (tracks) with positional args. */
data class CompiledSmartRule(val sql: String, val args: List<Any?>)

object SmartRuleCompiler {
    fun compile(rule: SmartRule): CompiledSmartRule {
        val args = ArrayList<Any?>()
        val sql = expression(rule, args)
        return CompiledSmartRule(sql, args)
    }

    private fun expression(rule: SmartRule, args: MutableList<Any?>): String = when (rule) {
        is SmartRule.All -> {
            if (rule.children.isEmpty()) "1 = 1"
            else rule.children.joinToString(" AND ", prefix = "(", postfix = ")") { expression(it, args) }
        }
        is SmartRule.Any -> {
            if (rule.children.isEmpty()) "0 = 1"
            else rule.children.joinToString(" OR ", prefix = "(", postfix = ")") { expression(it, args) }
        }
        is SmartRule.Not -> "NOT (${expression(rule.child, args)})"
        // Written as non-correlated IN (SELECT ...) so the genre scope is evaluated once per query,
        // not once per track (a correlated recursive CTE here made folder/genre lists very slow).
        is SmartRule.Genre -> if (rule.includeDescendants) {
            args += rule.genreId
            """
            t.track_id IN (
                SELECT sr_a.trackId FROM track_genre_assignments AS sr_a
                WHERE sr_a.genreId IN (
                    WITH RECURSIVE sr_scope(genreId) AS (
                        SELECT ?
                        UNION
                        SELECT e.childGenreId FROM genre_edges AS e
                        INNER JOIN sr_scope AS p ON p.genreId = e.parentGenreId
                    )
                    SELECT genreId FROM sr_scope
                )
            )
            """.trimIndent()
        } else {
            args += rule.genreId
            "t.track_id IN (SELECT sr_a.trackId FROM track_genre_assignments AS sr_a WHERE sr_a.genreId = ?)"
        }
        SmartRule.NoGenre -> "NOT EXISTS (SELECT 1 FROM track_genre_assignments AS sr_a WHERE sr_a.trackId = t.track_id)"
        is SmartRule.ArtistIs -> {
            args += rule.artist
            "${effectiveField("ARTIST", "normalizedArtist")} = ?"
        }
        is SmartRule.AlbumIs -> {
            args += rule.album
            "${effectiveField("ALBUM", "normalizedAlbum")} = ?"
        }
        is SmartRule.TitleContains -> {
            args += rule.text
            "instr(lower(COALESCE(${effectiveField("TITLE", "normalizedTitle")}, '')), lower(?)) > 0"
        }
    }

    /** Override value if present, otherwise the normalized metadata column. */
    private fun effectiveField(fieldName: String, normalizedColumn: String): String =
        "(SELECT COALESCE((SELECT o.value FROM track_metadata_overrides AS o " +
            "WHERE o.trackId = t.track_id AND o.fieldName = '$fieldName'), m.$normalizedColumn) " +
            "FROM track_metadata AS m WHERE m.trackId = t.track_id)"
}
