package uk.kayalab.mynotes.data

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
    val thumbnail: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean = other is Note && other.id == id && other.updatedAt == updatedAt && other.name == name
    override fun hashCode(): Int = id.hashCode()
}
