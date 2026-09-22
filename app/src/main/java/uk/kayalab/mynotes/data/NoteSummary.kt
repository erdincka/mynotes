package uk.kayalab.mynotes.data

data class NoteSummary(
    val id: Long,
    val name: String,
    val folderId: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val contentSize: Int
)
