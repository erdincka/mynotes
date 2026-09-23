package uk.kayalab.mynotes.ui

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import uk.kayalab.mynotes.data.Note
import uk.kayalab.mynotes.data.NoteRepository
import uk.kayalab.mynotes.data.PageTemplate
import uk.kayalab.mynotes.data.SettingsRepository
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import uk.kayalab.mynotes.export.ImageStore
import uk.kayalab.mynotes.export.PdfExportService
import uk.kayalab.mynotes.recognition.HandwritingRecognizer
import uk.kayalab.mynotes.ui.canvas.StrokeData
import uk.kayalab.mynotes.ui.canvas.StrokeGeometry
import javax.inject.Inject

sealed interface NoteLoadState {
    data object Loading : NoteLoadState
    data object Ready : NoteLoadState
    data object Missing : NoteLoadState
    /** Loading threw; editing stays disabled so nothing is written over data we could not read. */
    data class Failed(val reason: String) : NoteLoadState
}

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val settingsRepository: SettingsRepository,
    private val noteSaver: NoteSaver,
    private val pdfExportService: PdfExportService,
    private val imageStore: ImageStore,
    private val recognizer: HandwritingRecognizer
) : ViewModel() {

    private val _recognizedText = MutableStateFlow<String?>(null)
    /** Text to show in the "Copy text" dialog; null when the dialog is closed. */
    val recognizedText: StateFlow<String?> = _recognizedText.asStateFlow()

    private val _isRecognizing = MutableStateFlow(false)
    val isRecognizing: StateFlow<Boolean> = _isRecognizing.asStateFlow()

    fun imageBitmap(name: String): ImageBitmap? = imageStore.bitmap(name)?.asImageBitmap()

    /** Imports the picked image and places it with its top-left at [topLeft], scaled to [maxWidth] content px. */
    fun insertImage(uri: Uri, topLeft: Offset, maxWidth: Float) {
        viewModelScope.launch {
            imageStore.import(uri)
                .onSuccess { imported ->
                    val scale = minOf(1f, maxWidth / imported.width)
                    addStroke(
                        StrokeData(
                            points = listOf(topLeft),
                            tool = "image",
                            imageName = imported.name,
                            imageWidth = imported.width * scale,
                            imageHeight = imported.height * scale
                        )
                    )
                }
                .onFailure { _message.value = "Could not insert the image: ${it.message}" }
        }
    }

    fun recognizeNow() {
        viewModelScope.launch {
            _isRecognizing.value = true
            recognizer.recognize(_strokes.value)
                .onSuccess { text ->
                    _recognizedText.value = text.ifBlank { "Nothing recognised on this page." }
                    _note.value?.let { note -> runCatching { noteRepository.setRecognizedText(note.id, text) } }
                }
                .onFailure { _message.value = it.message ?: "Recognition failed." }
            _isRecognizing.value = false
        }
    }

    fun dismissRecognizedText() {
        _recognizedText.value = null
    }

    private val _note = MutableStateFlow<Note?>(null)
    val note: StateFlow<Note?> = _note.asStateFlow()

    private val _loadState = MutableStateFlow<NoteLoadState>(NoteLoadState.Loading)
    val loadState: StateFlow<NoteLoadState> = _loadState.asStateFlow()

    private val _strokes = MutableStateFlow<List<StrokeData>>(emptyList())
    val strokes: StateFlow<List<StrokeData>> = _strokes.asStateFlow()

    private val _selectedStrokeIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedStrokeIds: StateFlow<Set<Long>> = _selectedStrokeIds.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val undoStack = ArrayDeque<List<StrokeData>>()
    private val redoStack = ArrayDeque<List<StrokeData>>()
    private var autosaveJob: Job? = null

    val canEdit: Boolean get() = _loadState.value == NoteLoadState.Ready

    fun loadNote(noteId: Long) {
        if (_note.value?.id == noteId) return
        viewModelScope.launch {
            val result = runCatching {
                val note = noteRepository.getNoteById(noteId) ?: return@runCatching null
                note to noteRepository.loadStrokes(noteId)
            }
            result.onFailure {
                Timber.e(it, "Loading note %d failed", noteId)
                _loadState.value = NoteLoadState.Failed(it.message ?: "unknown error")
            }.onSuccess { loaded ->
                if (loaded == null) {
                    _loadState.value = NoteLoadState.Missing
                    return@onSuccess
                }
                val (note, strokes) = loaded
                imageStore.preload(strokes.mapNotNull { it.imageName })
                _note.value = note
                _strokes.value = strokes
                noteSaver.prime(noteId, strokes)
                _loadState.value = NoteLoadState.Ready
            }
        }
    }

    /** Persists immediately if there are unsaved changes. Safe to call on the way out of the screen. */
    fun saveNow() {
        val note = _note.value ?: return
        if (!canEdit || !_isDirty.value) return
        autosaveJob?.cancel()
        val snapshot = _strokes.value
        _isDirty.value = false
        noteSaver.save(note.id, snapshot) { result ->
            result.onFailure {
                _isDirty.value = true
                _message.value = "Could not save the note. Your changes are still on screen; try again."
            }
        }
    }

    fun sharePdf() {
        val note = _note.value ?: return
        saveNow()
        viewModelScope.launch {
            when (val outcome = pdfExportService.share(note.name, _strokes.value, PageTemplate.fromName(note.template))) {
                is PdfExportService.Outcome.Failed -> _message.value = "Could not share the PDF: ${outcome.message}"
                is PdfExportService.Outcome.Saved -> Unit
            }
        }
    }

    fun exportPdf() {
        val note = _note.value ?: return
        saveNow()
        viewModelScope.launch {
            val folder = runCatching { settingsRepository.exportFolderUri.first() }.getOrNull()
            _message.value = when (val outcome = pdfExportService.exportToFolder(note.name, _strokes.value, folder, PageTemplate.fromName(note.template))) {
                is PdfExportService.Outcome.Failed -> "Export failed: ${outcome.message}"
                is PdfExportService.Outcome.Saved -> "Exported to ${outcome.displayPath}"
            }
        }
    }

    fun setTemplate(template: PageTemplate) {
        val note = _note.value ?: return
        _note.value = note.copy(template = template.name.lowercase())
        viewModelScope.launch {
            runCatching { noteRepository.setTemplate(note.id, template) }
                .onFailure { Timber.e(it, "Changing paper failed"); _message.value = "Could not change the paper." }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun addStroke(stroke: StrokeData) = commit(_strokes.value + stroke)

    fun startErasing() {
        if (!canEdit) return
        pushUndo()
    }

    fun eraseAt(point: Offset, radius: Float) {
        if (!canEdit) return
        StrokeGeometry.erase(_strokes.value, point, radius)?.let { commit(it, recordUndo = false) }
    }

    fun selectStrokesInPath(pathPoints: List<Offset>) {
        if (!canEdit) return
        val result = StrokeGeometry.lassoSelect(_strokes.value, pathPoints)
        if (result.strokes !== _strokes.value) commit(result.strokes)
        _selectedStrokeIds.value = result.selectedIds
    }

    fun moveSelectedStrokes(delta: Offset) {
        if (!canEdit || _selectedStrokeIds.value.isEmpty() || delta == Offset.Zero) return
        commit(StrokeGeometry.move(_strokes.value, _selectedStrokeIds.value, delta))
    }

    fun clearSelection() {
        _selectedStrokeIds.value = emptySet()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_strokes.value)
        setStrokes(previous)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_strokes.value)
        setStrokes(next)
    }

    private fun commit(newStrokes: List<StrokeData>, recordUndo: Boolean = true) {
        if (!canEdit) return
        if (recordUndo) pushUndo()
        setStrokes(newStrokes)
    }

    private fun pushUndo() {
        undoStack.addLast(_strokes.value)
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun setStrokes(newStrokes: List<StrokeData>) {
        _strokes.value = newStrokes
        _isDirty.value = true
        scheduleAutosave()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            saveNow()
        }
    }

    override fun onCleared() {
        saveNow()
        _note.value?.let { noteSaver.forget(it.id) }
    }

    private companion object {
        const val MAX_UNDO = 50
        const val AUTOSAVE_DELAY_MS = 3000L
    }
}
