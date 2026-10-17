package com.theemrock.smoothmedia.genre

data class GenreNode(val id: String, val parentIds: Set<String> = emptySet())

sealed interface GenreQuery {
    fun matches(values: Set<String>): Boolean

    data class Term(val id: String) : GenreQuery {
        override fun matches(values: Set<String>): Boolean = id.trim().lowercase() in values
    }

    data class And(val children: List<GenreQuery>) : GenreQuery {
        override fun matches(values: Set<String>): Boolean = children.all { it.matches(values) }
    }

    data class Or(val children: List<GenreQuery>) : GenreQuery {
        override fun matches(values: Set<String>): Boolean = children.any { it.matches(values) }
    }

    data class Not(val child: GenreQuery) : GenreQuery {
        override fun matches(values: Set<String>): Boolean = !child.matches(values)
    }
}

sealed interface GenreBuildResult {
    data class Valid(val value: GenreHierarchy) : GenreBuildResult
    data class Invalid(val reason: String) : GenreBuildResult
}

class GenreHierarchy private constructor(nodes: List<GenreNode>) {
    private val byId = nodes.associateBy { it.id }
    private val children = nodes.flatMap { node -> node.parentIds.map { it to node.id } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, ids) -> ids.sorted() }

    fun parentsOf(id: String): List<String> = byId[id]?.parentIds?.sorted().orEmpty()

    fun descendantsOf(id: String): List<String> {
        val result = sortedSetOf<String>()
        val pending = ArrayDeque(children[id].orEmpty())
        while (pending.isNotEmpty()) {
            val child = pending.removeFirst()
            if (result.add(child)) pending.addAll(children[child].orEmpty())
        }
        return result.toList()
    }

    companion object {
        fun build(nodes: List<GenreNode>): GenreBuildResult {
            val normalized = nodes.map { node -> node.copy(id = node.id.trim(), parentIds = node.parentIds.map(String::trim).filter(String::isNotEmpty).toSet()) }
            if (normalized.any { it.id.isEmpty() }) return GenreBuildResult.Invalid("Genre ID boş olamaz")
            if (normalized.map { it.id }.toSet().size != normalized.size) return GenreBuildResult.Invalid("Genre ID tekrarı")
            val ids = normalized.map { it.id }.toSet()
            val missing = normalized.flatMap { it.parentIds }.firstOrNull { it !in ids }
            if (missing != null) return GenreBuildResult.Invalid("Eksik parent: $missing")
            val parents = normalized.associate { it.id to it.parentIds }
            val visiting = mutableSetOf<String>()
            val visited = mutableSetOf<String>()
            fun visit(id: String): Boolean {
                if (!visiting.add(id)) return false
                if (id in visited) { visiting.remove(id); return true }
                if (parents[id].orEmpty().any { !visit(it) }) return false
                visiting.remove(id)
                visited.add(id)
                return true
            }
            if (ids.any { !visit(it) }) return GenreBuildResult.Invalid("Genre parent döngüsü")
            return GenreBuildResult.Valid(GenreHierarchy(normalized))
        }
    }
}
