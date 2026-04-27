package com.mynotes.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
     @Insert
    suspend fun insert(folder: Folder): Long

     @Update
    suspend fun update(folder: Folder)

     @Delete
    suspend fun delete(folder: Folder)

     @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Long): Folder?

     @Query("SELECT * FROM folders WHERE parentId = :parentId ORDER BY name ASC")
    fun getFoldersByParent(parentId: Long?): Flow<List<Folder>>

     @Query("SELECT * FROM folders ORDER BY parentId ASC, name ASC")
    fun getAllFolders(): Flow<List<Folder>>

     @Query("SELECT * FROM folders WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchFolders(query: String): Flow<List<Folder>>

     @Query("UPDATE folders SET parentId = :newParentId, updatedAt = :timestamp WHERE id = :folderId")
    suspend fun moveFolderTo(folderId: Long, newParentId: Long?, timestamp: Long = System.currentTimeMillis())

     @Query("DELETE FROM folders WHERE parentId = :parentId")
    suspend fun deleteChildFolders(parentId: Long)
}