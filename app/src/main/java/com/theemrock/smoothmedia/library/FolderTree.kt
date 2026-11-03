package com.theemrock.smoothmedia.library

/** One real SAF directory in the folder tree. Values come from saf_documents and the catalog. */
data class FolderTreeNode(
    val sourceId: String,
    val documentId: String,
    val name: String,
    /** Tracks in this folder and all of its subfolders. */
    val trackCount: Int,
    /** True when the folder has at least one available subdirectory. */
    val hasChildren: Boolean,
) {
    val key: String get() = folderTreeKey(sourceId, documentId)
}

/** A visible line of the tree: depth decides indentation, expanded decides the chevron. */
data class FolderTreeRow(
    val node: FolderTreeNode,
    val depth: Int,
    val expanded: Boolean,
)

fun folderTreeKey(sourceId: String, documentId: String): String = sourceId + '\u001E' + documentId

/** An available SAF directory, as stored. */
data class FolderDir(val sourceId: String, val documentId: String, val parentDocumentId: String?, val name: String)

/** A catalog track whose file sits directly in [parentDocumentId]. */
data class FolderTrackRef(val sourceId: String, val parentDocumentId: String, val trackId: String)

/**
 * In-memory folder tree built from two bulk reads. Track counts are unique tracks in the
 * whole subtree (same meaning as before), computed once per folder, not one query per folder.
 */
class FolderTreeIndex(dirs: List<FolderDir>, files: List<FolderTrackRef>) {
    private val dirByKey: Map<String, FolderDir> = dirs.associateBy { folderTreeKey(it.sourceId, it.documentId) }
    private val childDirs: Map<String, List<FolderDir>> = dirs
        .filter { it.parentDocumentId != null }
        .groupBy { folderTreeKey(it.sourceId, it.parentDocumentId!!) }
        .mapValues { (_, list) -> list.sortedBy { it.name.lowercase() } }
    private val directTracks: Map<String, Set<String>> = files
        .groupBy({ folderTreeKey(it.sourceId, it.parentDocumentId) }, { it.trackId })
        .mapValues { (_, ids) -> ids.toHashSet() }
    private val subtreeCache = HashMap<String, Set<String>>()

    val roots: List<FolderTreeNode> = dirByKey.values
        .filter { it.parentDocumentId == null || folderTreeKey(it.sourceId, it.parentDocumentId) !in dirByKey }
        .sortedBy { it.name.lowercase() }
        .map(::toNode)

    fun childrenOf(key: String): List<FolderTreeNode> = childDirs[key].orEmpty().map(::toNode)

    private fun toNode(dir: FolderDir): FolderTreeNode {
        val key = folderTreeKey(dir.sourceId, dir.documentId)
        return FolderTreeNode(
            sourceId = dir.sourceId,
            documentId = dir.documentId,
            name = dir.name,
            trackCount = subtree(key, HashSet()).size,
            hasChildren = childDirs[key].orEmpty().isNotEmpty(),
        )
    }

    private fun subtree(key: String, visiting: MutableSet<String>): Set<String> {
        subtreeCache[key]?.let { return it }
        if (!visiting.add(key)) return emptySet()
        val result = HashSet<String>(directTracks[key].orEmpty())
        childDirs[key].orEmpty().forEach { child ->
            result.addAll(subtree(folderTreeKey(child.sourceId, child.documentId), visiting))
        }
        visiting.remove(key)
        return result.also { subtreeCache[key] = it }
    }
}

/**
 * Flattens the tree into visible rows, depth-first, children sorted as given.
 * [children] holds already-loaded child nodes per parent key; a folder whose children
 * are not loaded yet simply shows no branch until they arrive.
 */
object FolderTreeFlattener {
    fun flatten(
        roots: List<FolderTreeNode>,
        children: Map<String, List<FolderTreeNode>>,
        expanded: Set<String>,
    ): List<FolderTreeRow> {
        val rows = ArrayList<FolderTreeRow>()
        fun visit(node: FolderTreeNode, depth: Int) {
            val isExpanded = node.key in expanded
            rows += FolderTreeRow(node, depth, isExpanded)
            if (isExpanded) {
                children[node.key].orEmpty().forEach { visit(it, depth + 1) }
            }
        }
        roots.forEach { visit(it, 0) }
        return rows
    }
}
