package com.mynotes.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class NoteRepository @Inject constructor(private val noteDao: NoteDao) {

    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()

    fun getNotesByFolder(folderId: Long): Flow<List<Note>> = noteDao.getNotesByFolder(folderId)

    fun searchNotes(query: String): Flow<List<Note>> = noteDao.searchNotes(query)

    suspend fun getNoteById(id: Long): Note? = withContext(Dispatchers.IO) {
        noteDao.getNoteById(id)
    }

    suspend fun insert(note: Note): Long = withContext(Dispatchers.IO) {
        noteDao.insert(note)
    }

    suspend fun update(note: Note) = withContext(Dispatchers.IO) {
        noteDao.update(note)
    }

    suspend fun delete(note: Note) = withContext(Dispatchers.IO) {
        noteDao.delete(note)
    }

    suspend fun markAsSynced(id: Long) = withContext(Dispatchers.IO) {
        noteDao.markAsSynced(id)
    }

    suspend fun markAsUnsynced(id: Long) = withContext(Dispatchers.IO) {
        noteDao.markAsUnsynced(id)
    }
}
