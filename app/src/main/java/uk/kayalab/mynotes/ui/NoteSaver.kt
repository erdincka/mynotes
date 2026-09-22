package uk.kayalab.mynotes.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import uk.kayalab.mynotes.data.NoteRepository
import uk.kayalab.mynotes.data.StrokeEntity
import uk.kayalab.mynotes.export.NoteThumbnailRenderer
import uk.kayalab.mynotes.ui.canvas.StrokeData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists only what changed since the last save: strokes are immutable, so a stroke that is the
 * same instance at the same position needs nothing. Runs in an application-wide scope because
 * viewModelScope is cancelled the moment the note screen is popped.
 */
@Singleton
class NoteSaver @Inject constructor(private val noteRepository: NoteRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val writeLock = Mutex()

    private class Saved(val stroke: StrokeData, val ordinal: Int)

    /** Last persisted state per note, keyed by stroke id. Guarded by [writeLock]. */
    private val baselines = HashMap<Long, HashMap<Long, Saved>>()

    /** Call after loading so the first save diffs against what the database already holds. */
    suspend fun prime(noteId: Long, strokes: List<StrokeData>) {
        writeLock.withLock { baselines[noteId] = index(strokes) }
    }

    fun save(noteId: Long, strokes: List<StrokeData>, onResult: (Result<Unit>) -> Unit = {}): Job =
        scope.launch {
            val result = runCatching {
                writeLock.withLock {
                    val baseline = baselines[noteId] ?: index(noteRepository.loadStrokes(noteId))
                    val current = index(strokes)
                    val deleted = baseline.keys.filter { it !in current }
                    val upserts = ArrayList<StrokeEntity>()
                    for ((id, saved) in current) {
                        val before = baseline[id]
                        if (before == null || before.stroke !== saved.stroke || before.ordinal != saved.ordinal) {
                            upserts.add(StrokeEntity.from(saved.stroke, noteId, saved.ordinal))
                        }
                    }
                    if (deleted.isEmpty() && upserts.isEmpty()) return@withLock
                    val thumbnail = NoteThumbnailRenderer.render(strokes)
                    noteRepository.applyStrokeChanges(noteId, deleted, upserts, thumbnail)
                    baselines[noteId] = current
                }
            }
            result.onFailure { Timber.e(it, "Saving note %d failed", noteId) }
            onResult(result)
        }

    fun forget(noteId: Long) {
        scope.launch { writeLock.withLock { baselines.remove(noteId) } }
    }

    private fun index(strokes: List<StrokeData>): HashMap<Long, Saved> {
        val map = HashMap<Long, Saved>(strokes.size * 2)
        strokes.forEachIndexed { ordinal, stroke -> map[stroke.id] = Saved(stroke, ordinal) }
        return map
    }
}
