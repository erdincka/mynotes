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

    @Query(
        "SELECT id, name, folderId, createdAt, updatedAt, length(content) AS contentSize " +
            "FROM notes ORDER BY updatedAt DESC"
    )
    fun getAllSummaries(): Flow<List<NoteSummary>>

    @Query("UPDATE notes SET content = :content, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, updatedAt: Long)

    @Query("UPDATE notes SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, name: String, updatedAt: Long)

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun moveTo(id: Long, folderId: Long, updatedAt: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notes WHERE folderId IN (:folderIds)")
    suspend fun deleteByFolderIds(folderIds: List<Long>)
}
