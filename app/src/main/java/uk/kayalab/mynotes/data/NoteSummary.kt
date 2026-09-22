package uk.kayalab.mynotes.data

/** What the list needs: never the ink itself. */
class NoteSummary(
    val id: Long,
    val name: String,
    val folderId: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val strokeCount: Int,
    val thumbnail: ByteArray?
) {
    override fun equals(other: Any?): Boolean =
        other is NoteSummary && other.id == id && other.name == name && other.folderId == folderId &&
            other.updatedAt == updatedAt && other.strokeCount == strokeCount
    override fun hashCode(): Int = id.hashCode()
}
