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
import uk.kayalab.mynotes.ui.canvas.StrokeCodec
import uk.kayalab.mynotes.ui.canvas.StrokeData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves outlive the screen and its view model: a save started while navigating away must
 * still complete, so it runs in an application-wide scope rather than viewModelScope.
 */
@Singleton
class NoteSaver @Inject constructor(private val noteRepository: NoteRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val writeLock = Mutex()

    fun save(noteId: Long, strokes: List<StrokeData>, onResult: (Result<Unit>) -> Unit = {}): Job =
        scope.launch {
            val result = runCatching {
                val content = StrokeCodec.encode(strokes)
                writeLock.withLock { noteRepository.updateContent(noteId, content) }
            }
            result.onFailure { Timber.e(it, "Saving note %d failed", noteId) }
            onResult(result)
        }
}
