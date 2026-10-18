package com.theemrock.smoothmedia.genre

data class CompiledGenreQuery(
    val sql: String,
    val arguments: List<String>,
    val limit: Int,
    val offset: Int,
)

object GenreQueryCompiler {
    fun compile(query: GenreQuery, limit: Int, offset: Int): CompiledGenreQuery {
        require(limit >= 0) { "limit negatif olamaz" }
        require(offset >= 0) { "offset negatif olamaz" }

        val descendantTerms = mutableListOf<GenreQuery.Term>()
        val arguments = mutableListOf<String>()
        val predicate = compilePredicate(query, descendantTerms, arguments)
        val cteArguments = mutableListOf<String>()
        val cteSeeds = if (descendantTerms.isEmpty()) {
            "SELECT NULL AS root_id, NULL AS genre_id WHERE 0"
        } else {
            descendantTerms.joinToString(" UNION ") {
                val id = it.id.trim()
                cteArguments += id
                cteArguments += id
                "SELECT ? AS root_id, ? AS genre_id"
            }
        }
        val sql = """
            WITH RECURSIVE descendants(root_id, genre_id) AS (
                $cteSeeds
                UNION
                SELECT d.root_id, e.childGenreId
                FROM descendants d
                INNER JOIN genre_edges e ON e.parentGenreId = d.genre_id
            )
            SELECT DISTINCT t.track_id
            FROM tracks t
            WHERE t.catalog_lifecycle = 'ACTIVE'
              AND ($predicate)
            ORDER BY t.track_id
            LIMIT ? OFFSET ?
        """.trimIndent()
        return CompiledGenreQuery(
            sql = sql,
            arguments = cteArguments + arguments,
            limit = limit,
            offset = offset,
        )
    }

    private fun compilePredicate(
        query: GenreQuery,
        descendantTerms: MutableList<GenreQuery.Term>,
        arguments: MutableList<String>,
    ): String = when (query) {
        is GenreQuery.Term -> {
            val id = query.id.trim()
            if (query.includeDescendants) {
                descendantTerms += query
                arguments += id
                "EXISTS (SELECT 1 FROM track_genre_assignments a INNER JOIN descendants d ON d.genre_id = a.genreId WHERE a.trackId = t.track_id AND d.root_id = ?)"
            } else {
                arguments += id
                "EXISTS (SELECT 1 FROM track_genre_assignments a WHERE a.trackId = t.track_id AND a.genreId = ?)"
            }
        }
        is GenreQuery.And -> if (query.children.isEmpty()) {
            "1"
        } else {
            query.children.joinToString(" AND ", prefix = "(", postfix = ")") {
                compilePredicate(it, descendantTerms, arguments)
            }
        }
        is GenreQuery.Or -> if (query.children.isEmpty()) {
            "0"
        } else {
            query.children.joinToString(" OR ", prefix = "(", postfix = ")") {
                compilePredicate(it, descendantTerms, arguments)
            }
        }
        is GenreQuery.Not -> if (query.child is GenreQuery.Term) {
            compilePredicate(query.child, descendantTerms, arguments).replaceFirst("EXISTS", "NOT EXISTS")
        } else {
            "NOT (${compilePredicate(query.child, descendantTerms, arguments)})"
        }
    }
}
