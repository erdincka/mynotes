package uk.kayalab.mynotes.data

/** Pure helpers over the folder hierarchy. Root is represented by a null parent. */
object FolderTree {

    fun childrenOf(folders: List<Folder>, parentId: Long?): List<Folder> =
        folders.filter { it.parentId == parentId }

    /** Every folder id strictly below [rootId]. Tolerates cycles in corrupt data. */
    fun descendantIds(folders: List<Folder>, rootId: Long): Set<Long> {
        val byParent = folders.groupBy { it.parentId }
        val result = LinkedHashSet<Long>()
        val queue = ArrayDeque<Long>().apply { add(rootId) }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (child in byParent[current].orEmpty()) {
                if (child.id != rootId && result.add(child.id)) queue.add(child.id)
            }
        }
        return result
    }

    fun subtreeIds(folders: List<Folder>, rootId: Long): Set<Long> =
        descendantIds(folders, rootId) + rootId

    /** A folder may not move into itself or into any of its descendants. */
    fun canMove(folders: List<Folder>, folderId: Long, targetParentId: Long?): Boolean {
        if (targetParentId == null) return true
        if (targetParentId == folderId) return false
        return targetParentId !in descendantIds(folders, folderId)
    }

    /** Folders from the root down to and including [folderId]; empty for root or unknown ids. */
    fun pathTo(folders: List<Folder>, folderId: Long?): List<Folder> {
        val byId = folders.associateBy { it.id }
        val path = ArrayList<Folder>()
        var current = folderId
        val seen = HashSet<Long>()
        while (current != null && seen.add(current)) {
            val folder = byId[current] ?: break
            path.add(0, folder)
            current = folder.parentId
        }
        return path
    }

    fun pathLabel(folders: List<Folder>, folderId: Long?): String =
        pathTo(folders, folderId).joinToString(" / ") { it.name }
}
