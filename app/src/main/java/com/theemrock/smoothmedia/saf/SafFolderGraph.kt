package com.theemrock.smoothmedia.saf

data class SafDocument(
    val documentId: String,
    val parentDocumentId: String?,
    val displayName: String,
    val isDirectory: Boolean,
)

/** Provider-safe graph: opaque document IDs are keys only, never filesystem paths. */
class SafFolderGraph(documents: List<SafDocument>) {
    private val byId = documents.associateBy { it.documentId }
    private val children = documents.groupBy { it.parentDocumentId }

    fun childrenOf(parentDocumentId: String?): List<SafDocument> =
        children[parentDocumentId].orEmpty().sortedWith(compareByDescending<SafDocument> { it.isDirectory }.thenBy { it.displayName })

    fun parentOf(documentId: String): SafDocument? = byId[documentId]?.parentDocumentId?.let(byId::get)

    fun pathOf(documentId: String): String {
        val names = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        var current = byId[documentId]
        while (current != null && visited.add(current.documentId)) {
            names += current.displayName
            current = current.parentDocumentId?.let(byId::get)
        }
        return names.asReversed().joinToString("/")
    }

    fun stableKeyOf(documentId: String): String {
        val ids = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        var current = byId[documentId]
        while (current != null && visited.add(current.documentId)) {
            ids += current.documentId
            current = current.parentDocumentId?.let(byId::get)
        }
        return ids.asReversed().joinToString("/")
    }
}
