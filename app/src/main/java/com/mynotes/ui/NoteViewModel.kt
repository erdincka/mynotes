package com.mynotes.ui

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mynotes.data.Note
import com.mynotes.data.NoteRepository
import com.mynotes.sync.SyncScheduler
import com.mynotes.ui.canvas.StrokeData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {
    private val _note = MutableStateFlow<Note?>(null)
    val note: StateFlow<Note?> = _note

    private val _strokes = MutableStateFlow<List<StrokeData>>(emptyList())
    val strokes: StateFlow<List<StrokeData>> = _strokes

    private val _selectedStrokeIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedStrokeIds: StateFlow<Set<Long>> = _selectedStrokeIds

    private var undoStack = mutableListOf<List<StrokeData>>()
    private var redoStack = mutableListOf<List<StrokeData>>()

    fun loadNote(noteId: Long) {
        viewModelScope.launch {
            val note = noteRepository.getNoteById(noteId)
            _note.value = note
            note?.content?.let {
                try {
                    _strokes.value = Json.decodeFromString(it)
                } catch (_: Exception) {
                    _strokes.value = emptyList()
                }
            }
        }
    }

    fun saveNote() {
        val currentNote = _note.value ?: return
        viewModelScope.launch {
            val content = Json.encodeToString(_strokes.value)
            noteRepository.update(currentNote.copy(content = content, updatedAt = System.currentTimeMillis()))
            syncScheduler.scheduleSync(immediate = false) // Schedule background sync
        }
    }

    fun addStroke(stroke: StrokeData) {
        saveToUndoStack()
        _strokes.value = _strokes.value + stroke
        redoStack.clear()
    }

    fun startErasing() {
        saveToUndoStack()
        redoStack.clear()
    }

    fun selectStrokesInPath(pathPoints: List<Offset>) {
        if (pathPoints.size < 3) {
            _selectedStrokeIds.value = emptySet()
            return
        }
        
        val androidPath = android.graphics.Path().apply {
            moveTo(pathPoints[0].x, pathPoints[0].y)
            for (i in 1 until pathPoints.size) lineTo(pathPoints[i].x, pathPoints[i].y)
            close()
        }
        val region = android.graphics.Region()
        val bounds = android.graphics.RectF()
        @Suppress("DEPRECATION")
        androidPath.computeBounds(bounds, true)
        region.setPath(androidPath, android.graphics.Region(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()))

        val currentStrokes = _strokes.value
        val newStrokesList = mutableListOf<StrokeData>()
        val selectedIds = mutableSetOf<Long>()
        var splitOccurred = false

        for (stroke in currentStrokes) {
            val inSegments = mutableListOf<MutableList<Int>>()
            val outSegments = mutableListOf<MutableList<Int>>()
            var currentSegment = mutableListOf<Int>()
            var wasInside: Boolean? = null

            for (i in stroke.points.indices) {
                val inside = region.contains(stroke.points[i].x.toInt(), stroke.points[i].y.toInt())
                if (wasInside == null) {
                    wasInside = inside
                    currentSegment.add(i)
                } else if (inside == wasInside) {
                    currentSegment.add(i)
                } else {
                    if (wasInside) inSegments.add(currentSegment) else outSegments.add(currentSegment)
                    currentSegment = mutableListOf(i)
                    wasInside = inside
                    splitOccurred = true
                }
            }
            if (wasInside != null) {
                if (wasInside) inSegments.add(currentSegment) else outSegments.add(currentSegment)
            }

            // Create new strokes from segments
            for (seg in inSegments) {
                if (seg.size < 1) continue
                val newStroke = stroke.copy(
                    id = if (inSegments.size == 1 && outSegments.isEmpty()) stroke.id else System.nanoTime(),
                    points = seg.map { stroke.points[it] },
                    pressures = seg.map { if (it < stroke.pressures.size) stroke.pressures[it] else 1f }
                )
                newStrokesList.add(newStroke)
                selectedIds.add(newStroke.id)
            }
            for (seg in outSegments) {
                if (seg.size < 1) continue
                val newStroke = stroke.copy(
                    id = if (outSegments.size == 1 && inSegments.isEmpty()) stroke.id else System.nanoTime(),
                    points = seg.map { stroke.points[it] },
                    pressures = seg.map { if (it < stroke.pressures.size) stroke.pressures[it] else 1f }
                )
                newStrokesList.add(newStroke)
            }
        }

        if (splitOccurred) {
            _strokes.value = newStrokesList
        }
        _selectedStrokeIds.value = selectedIds
    }

    fun moveSelectedStrokes(delta: Offset) {
        if (_selectedStrokeIds.value.isEmpty()) return
        
        val newStrokes = _strokes.value.map { stroke ->
            if (stroke.id in _selectedStrokeIds.value) {
                stroke.copy(points = stroke.points.map { it + delta })
            } else {
                stroke
            }
        }
        _strokes.value = newStrokes
    }

    fun commitMove() {
        if (_selectedStrokeIds.value.isNotEmpty()) {
            saveToUndoStack()
            redoStack.clear()
        }
    }

    fun clearSelection() {
        _selectedStrokeIds.value = emptySet()
    }

    fun eraseAt(point: Offset, radius: Float) {
        val currentStrokes = _strokes.value
        var changed = false
        val newStrokesList = mutableListOf<StrokeData>()

        for (stroke in currentStrokes) {
            // Quick check: is the point anywhere near this stroke's bounding box?
            // For simplicity, we just check points, but we could optimize further.
            val threshold = radius + (stroke.strokeWidth / 2)
            val isPossiblyNear = stroke.points.any { (it - point).getDistance() <= threshold + 50f } // broad check
            
            if (!isPossiblyNear) {
                newStrokesList.add(stroke)
                continue
            }

            val keptSegments = mutableListOf<MutableList<Int>>()
            var currentSegment = mutableListOf<Int>()
            
            for (i in stroke.points.indices) {
                val distance = (stroke.points[i] - point).getDistance()
                if (distance > threshold) {
                    currentSegment.add(i)
                } else {
                    if (currentSegment.isNotEmpty()) {
                        keptSegments.add(currentSegment)
                        currentSegment = mutableListOf()
                    }
                    changed = true
                }
            }
            if (currentSegment.isNotEmpty()) keptSegments.add(currentSegment)

            if (keptSegments.isEmpty()) {
                changed = true
                // Stroke completely erased
            } else if (keptSegments.size == 1 && keptSegments[0].size == stroke.points.size) {
                newStrokesList.add(stroke)
            } else {
                for (segmentIndices in keptSegments) {
                    if (segmentIndices.isEmpty()) continue
                    newStrokesList.add(stroke.copy(
                        id = System.nanoTime(),
                        points = segmentIndices.map { stroke.points[it] },
                        pressures = segmentIndices.map { if (it < stroke.pressures.size) stroke.pressures[it] else 1f }
                    ))
                }
                changed = true
            }
        }

        if (changed) {
            _strokes.value = newStrokesList
        }
    }

    private fun saveToUndoStack() {
        undoStack.add(_strokes.value.toList())
        if (undoStack.size > 50) undoStack.removeAt(0)
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(_strokes.value.toList())
            _strokes.value = undoStack.removeAt(undoStack.size - 1)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(_strokes.value.toList())
            _strokes.value = redoStack.removeAt(redoStack.size - 1)
        }
    }
}
