package com.mynotes.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?

    @Query("SELECT * FROM notes WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun getNotesByFolder(folderId: Long): Flow<List<Note>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE name LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchNotes(query: String): Flow<List<Note>>
}
