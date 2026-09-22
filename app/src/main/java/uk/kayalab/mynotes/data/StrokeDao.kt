package uk.kayalab.mynotes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StrokeDao {
    @Query("SELECT * FROM strokes WHERE noteId = :noteId ORDER BY ordinal ASC")
    suspend fun forNote(noteId: Long): List<StrokeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(strokes: List<StrokeEntity>)

    @Query("DELETE FROM strokes WHERE noteId = :noteId AND id IN (:ids)")
    suspend fun delete(noteId: Long, ids: List<Long>)

    @Query("SELECT COUNT(*) FROM strokes WHERE noteId = :noteId")
    suspend fun count(noteId: Long): Int
}
