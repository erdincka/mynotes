package uk.kayalab.mynotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import uk.kayalab.mynotes.data.Folder
import uk.kayalab.mynotes.data.FolderRepository
import uk.kayalab.mynotes.data.FolderTree
import uk.kayalab.mynotes.data.NoteRepository
import uk.kayalab.mynotes.data.NoteSummary
import uk.kayalab.mynotes.data.SettingsRepository
import uk.kayalab.mynotes.export.PdfExportService
import javax.inject.Inject

/** Notes at the top level have no folder; the database stores that as folder id 0. */
const val ROOT_FOLDER_ID = 0L

enum class SortOrder { NAME, DATE }

/** What a delete would remove, shown to the user before anything happens. */
data class DeleteRequest(
    val folderIds: Set<Long>,
    val noteIds: Set<Long>,
    val title: String,
    val folderCount: Int,
    val noteCount: Int
)

data class FolderListState(
    val folders: List<Folder> = emptyList(),
    val notes: List<NoteSummary> = emptyList(),
    val expandedFolders: Set<Long> = emptySet(),
    val selectedNotes: Set<Long> = emptySet(),
    val selectedFolders: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val sortOrder: SortOrder = SortOrder.DATE,
    val searchQuery: String = "",
    val pendingDelete: DeleteRequest? = null,
    val message: String? = null
) {
    val isSearching: Boolean get() = searchQuery.isNotBlank()
    val hasSelection: Boolean get() = selectedNotes.isNotEmpty() || selectedFolders.isNotEmpty()
}

@HiltViewModel
class FolderListViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val noteRepository: NoteRepository,
    private val settingsRepository: SettingsRepository,
    private val pdfExportService: PdfExportService
) : ViewModel() {

    private val _state = MutableStateFlow(FolderListState())
    val state: StateFlow<FolderListState> = _state.asStateFlow()

    private val sortOrder = MutableStateFlow(SortOrder.DATE)
    private val searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            combine(folderRepository.allFolders, noteRepository.allSummaries, sortOrder, searchQuery) { folders, notes, order, query ->
                val visibleFolders = if (query.isBlank()) folders else folders.filter { it.name.contains(query, ignoreCase = true) }
                val visibleNotes = if (query.isBlank()) notes else notes.filter { it.name.contains(query, ignoreCase = true) }
                Triple(sortFolders(visibleFolders, order), sortNotes(visibleNotes, order), order to query)
            }.collect { (folders, notes, orderAndQuery) ->
                _state.update {
                    it.copy(
                        folders = folders,
                        notes = notes,
                        isLoading = false,
                        sortOrder = orderAndQuery.first,
                        searchQuery = orderAndQuery.second
                    )
                }
            }
        }
    }

    /** The complete folder list, unfiltered, for path labels, move targets and cascade deletes. */
    val allFolders: StateFlow<List<Folder>> = folderRepository.allFolders
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setSortOrder(order: SortOrder) { sortOrder.value = order }
    fun setSearchQuery(query: String) { searchQuery.value = query }

    fun toggleFolder(folderId: Long) = _state.update {
        it.copy(expandedFolders = it.expandedFolders.toggled(folderId))
    }

    /** Opens every ancestor so a search hit becomes visible in the tree. */
    fun revealFolder(folderId: Long) {
        val ancestors = FolderTree.pathTo(allFolders.value, folderId).map { it.id }
        searchQuery.value = ""
        _state.update { it.copy(expandedFolders = it.expandedFolders + ancestors) }
    }

    fun toggleNoteSelection(noteId: Long) = _state.update { it.copy(selectedNotes = it.selectedNotes.toggled(noteId)) }
    fun toggleFolderSelection(folderId: Long) = _state.update { it.copy(selectedFolders = it.selectedFolders.toggled(folderId)) }
    fun clearSelection() = _state.update { it.copy(selectedNotes = emptySet(), selectedFolders = emptySet()) }

    fun createFolder(name: String, parentId: Long?) = launchSafely("create the folder") {
        folderRepository.create(name.trim(), parentId)
    }

    fun createNote(name: String, folderId: Long, onCreated: (Long) -> Unit) = launchSafely("create the note") {
        onCreated(noteRepository.create(name.trim(), folderId))
    }

    fun renameNote(noteId: Long, newName: String) = launchSafely("rename the note") {
        noteRepository.rename(noteId, newName.trim())
    }

    fun renameFolder(folderId: Long, newName: String) = launchSafely("rename the folder") {
        folderRepository.rename(folderId, newName.trim())
    }

    fun canMoveFolder(folderId: Long, targetParentId: Long?): Boolean =
        FolderTree.canMove(allFolders.value, folderId, targetParentId)

    fun moveSelectedTo(targetFolderId: Long?) = launchSafely("move the selection") {
        val current = _state.value
        current.selectedNotes.forEach { noteRepository.moveTo(it, targetFolderId ?: ROOT_FOLDER_ID) }
        val blocked = current.selectedFolders.filterNot { canMoveFolder(it, targetFolderId) }
        current.selectedFolders.filter { it !in blocked }.forEach { folderRepository.moveTo(it, targetFolderId) }
        _state.update {
            it.copy(
                selectedNotes = emptySet(),
                selectedFolders = emptySet(),
                message = if (blocked.isEmpty()) null else "A folder cannot be moved inside itself."
            )
        }
    }

    fun requestDeleteNote(note: NoteSummary) = _state.update {
        it.copy(pendingDelete = DeleteRequest(emptySet(), setOf(note.id), note.name, 0, 1))
    }

    fun requestDeleteFolder(folder: Folder) = _state.update { it.copy(pendingDelete = deleteRequestFor(setOf(folder.id), emptySet(), folder.name)) }

    fun requestDeleteSelection() = _state.update {
        if (!it.hasSelection) it
        else it.copy(pendingDelete = deleteRequestFor(it.selectedFolders, it.selectedNotes, "the selected items"))
    }

    fun cancelDelete() = _state.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val request = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }
        launchSafely("delete") {
            val folders = allFolders.value
            request.folderIds.forEach { folderRepository.deleteTree(it, folders) }
            request.noteIds.forEach { noteRepository.delete(it) }
            _state.update { it.copy(selectedNotes = emptySet(), selectedFolders = emptySet()) }
        }
    }

    fun exportNoteToPdf(noteId: Long) = launchSafely("export the PDF") {
        val note = noteRepository.getNoteById(noteId) ?: return@launchSafely
        val strokes = noteRepository.loadStrokes(noteId)
        val folder = settingsRepository.exportFolderUri.first()
        when (val outcome = pdfExportService.exportToFolder(note.name, strokes, folder)) {
            is PdfExportService.Outcome.Saved -> showMessage("Exported to ${outcome.displayPath}")
            is PdfExportService.Outcome.Failed -> showMessage("Export failed: ${outcome.message}")
        }
    }

    fun shareNoteAsPdf(noteId: Long) = launchSafely("share the PDF") {
        val note = noteRepository.getNoteById(noteId) ?: return@launchSafely
        val strokes = noteRepository.loadStrokes(noteId)
        when (val outcome = pdfExportService.share(note.name, strokes)) {
            is PdfExportService.Outcome.Saved -> Unit
            is PdfExportService.Outcome.Failed -> showMessage("Could not share the PDF: ${outcome.message}")
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun deleteRequestFor(folderIds: Set<Long>, noteIds: Set<Long>, title: String): DeleteRequest {
        val folders = allFolders.value
        val allFolderIds = folderIds.flatMapTo(HashSet()) { FolderTree.subtreeIds(folders, it) }
        val notesInFolders = _state.value.notes.filter { it.folderId in allFolderIds }.map { it.id }
        return DeleteRequest(
            folderIds = folderIds,
            noteIds = noteIds,
            title = title,
            folderCount = allFolderIds.size,
            noteCount = (noteIds + notesInFolders).size
        )
    }

    private fun showMessage(text: String) = _state.update { it.copy(message = text) }

    private fun launchSafely(action: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure {
                Timber.e(it, "Failed to %s", action)
                showMessage("Could not $action: ${it.message ?: "unexpected error"}")
            }
        }
    }

    private fun sortFolders(folders: List<Folder>, order: SortOrder) = when (order) {
        SortOrder.NAME -> folders.sortedBy { it.name.lowercase() }
        SortOrder.DATE -> folders.sortedByDescending { it.updatedAt }
    }

    private fun sortNotes(notes: List<NoteSummary>, order: SortOrder) = when (order) {
        SortOrder.NAME -> notes.sortedBy { it.name.lowercase() }
        SortOrder.DATE -> notes.sortedByDescending { it.updatedAt }
    }

    private fun Set<Long>.toggled(id: Long): Set<Long> = if (id in this) this - id else this + id
}
