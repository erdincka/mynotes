package uk.kayalab.mynotes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Insert
    suspend fun insert(folder: Folder): Long

    @Query("SELECT * FROM folders ORDER BY parentId ASC, name ASC")
    fun getAllFolders(): Flow<List<Folder>>

    @Query("UPDATE folders SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, name: String, updatedAt: Long)

    @Query("UPDATE folders SET parentId = :parentId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun moveTo(id: Long, parentId: Long?, updatedAt: Long)

    @Query("DELETE FROM folders WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
