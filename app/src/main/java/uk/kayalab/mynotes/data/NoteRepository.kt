package uk.kayalab.mynotes.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import uk.kayalab.mynotes.ui.canvas.StrokeData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val database: MyNotesDatabase,
    private val noteDao: NoteDao,
    private val strokeDao: StrokeDao
) {
    val allSummaries: Flow<List<NoteSummary>> = noteDao.getAllSummaries()

    suspend fun getNoteById(id: Long): Note? = noteDao.getNoteById(id)

    suspend fun getAllPlain(): List<NotePlain> = noteDao.getAllPlain()

    suspend fun loadStrokes(noteId: Long): List<StrokeData> = strokeDao.forNote(noteId).map { it.toStrokeData() }

    suspend fun create(name: String, folderId: Long): Long =
        noteDao.insert(Note(name = name, folderId = folderId))

    /** Applies a stroke diff and refreshes the note's timestamp and thumbnail in one transaction. */
    suspend fun applyStrokeChanges(noteId: Long, deletedIds: List<Long>, upserts: List<StrokeEntity>, thumbnail: ByteArray?) {
        database.withTransaction {
            if (deletedIds.isNotEmpty()) deletedIds.chunked(500).forEach { strokeDao.delete(noteId, it) }
            if (upserts.isNotEmpty()) upserts.chunked(500).forEach { strokeDao.upsert(it) }
            noteDao.touch(noteId, System.currentTimeMillis(), thumbnail)
        }
    }

    /** Inserts a whole note with its strokes, used by restore. */
    suspend fun insertWithStrokes(note: Note, strokes: List<StrokeData>, thumbnail: ByteArray?): Long =
        database.withTransaction {
            val id = noteDao.insert(note)
            strokes.mapIndexed { index, stroke -> StrokeEntity.from(stroke, id, index) }
                .chunked(500).forEach { strokeDao.upsert(it) }
            if (thumbnail != null) noteDao.touch(id, note.updatedAt, thumbnail)
            id
        }

    suspend fun rename(id: Long, name: String) =
        noteDao.rename(id, name, System.currentTimeMillis())

    suspend fun moveTo(id: Long, folderId: Long) =
        noteDao.moveTo(id, folderId, System.currentTimeMillis())

    suspend fun delete(id: Long) = noteDao.deleteById(id)
}
