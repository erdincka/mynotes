package uk.kayalab.mynotes.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(private val noteDao: NoteDao) {

    val allSummaries: Flow<List<NoteSummary>> = noteDao.getAllSummaries()

    suspend fun getNoteById(id: Long): Note? = noteDao.getNoteById(id)

    suspend fun create(name: String, folderId: Long): Long =
        noteDao.insert(Note(name = name, folderId = folderId))

    suspend fun updateContent(id: Long, content: String) =
        noteDao.updateContent(id, content, System.currentTimeMillis())

    suspend fun rename(id: Long, name: String) =
        noteDao.rename(id, name, System.currentTimeMillis())

    suspend fun moveTo(id: Long, folderId: Long) =
        noteDao.moveTo(id, folderId, System.currentTimeMillis())

    suspend fun delete(id: Long) = noteDao.deleteById(id)
}
