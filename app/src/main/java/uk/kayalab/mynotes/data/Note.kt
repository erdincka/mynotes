package uk.kayalab.mynotes.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val folderId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val thumbnail: ByteArray? = null,
    @ColumnInfo(defaultValue = "grid")
    val template: String = "grid",
    @ColumnInfo(defaultValue = "")
    val recognizedText: String = ""
) {
    override fun equals(other: Any?): Boolean =
        other is Note && other.id == id && other.name == name && other.folderId == folderId &&
            other.createdAt == createdAt && other.updatedAt == updatedAt && other.template == template && other.recognizedText == recognizedText &&
            (other.thumbnail === thumbnail || (other.thumbnail != null && thumbnail != null && other.thumbnail.contentEquals(thumbnail)))
    override fun hashCode(): Int = id.hashCode()
}
