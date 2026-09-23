package uk.kayalab.mynotes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: Note): Long

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?

    @Query("SELECT id, name, folderId, createdAt, updatedAt, template FROM notes")
    suspend fun getAllPlain(): List<NotePlain>

    @Query(
        "SELECT id, name, folderId, createdAt, updatedAt, thumbnail, recognizedText, " +
            "(SELECT COUNT(*) FROM strokes WHERE strokes.noteId = notes.id) AS strokeCount " +
            "FROM notes ORDER BY updatedAt DESC"
    )
    fun getAllSummaries(): Flow<List<NoteSummary>>

    @Query("UPDATE notes SET updatedAt = :updatedAt, thumbnail = :thumbnail WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long, thumbnail: ByteArray?)

    @Query("SELECT id FROM notes WHERE recognizedText = ''")
    suspend fun idsWithoutRecognizedText(): List<Long>

    @Query("UPDATE notes SET recognizedText = :text WHERE id = :id")
    suspend fun updateRecognizedText(id: Long, text: String)

    @Query("UPDATE notes SET template = :template WHERE id = :id")
    suspend fun updateTemplate(id: Long, template: String)

    @Query("UPDATE notes SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, name: String, updatedAt: Long)

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun moveTo(id: Long, folderId: Long, updatedAt: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notes WHERE folderId IN (:folderIds)")
    suspend fun deleteByFolderIds(folderIds: List<Long>)
}

/** Metadata only, for backups. */
data class NotePlain(val id: Long, val name: String, val folderId: Long, val createdAt: Long, val updatedAt: Long, val template: String)
