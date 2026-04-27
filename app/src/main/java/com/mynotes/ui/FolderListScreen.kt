package com.mynotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.mynotes.data.Folder
import com.mynotes.data.FolderRepository
import com.mynotes.data.Note
import com.mynotes.data.NoteRepository
import com.mynotes.ui.canvas.PdfExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.roundToInt

enum class SortOrder {
    NAME, DATE
}

data class FolderListState(
    val folders: List<Folder> = emptyList(),
    val notes: List<Note> = emptyList(),
    val expandedFolders: Set<Long> = emptySet(),
    val selectedNotes: Set<Long> = emptySet(),
    val selectedFolders: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val sortOrder: SortOrder = SortOrder.NAME
)

@HiltViewModel
class FolderListViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val noteRepository: NoteRepository
) : ViewModel() {
    private val _state = MutableStateFlow(FolderListState())
    val state: StateFlow<FolderListState> = _state
    
    private val _sortOrder = MutableStateFlow(SortOrder.NAME)

    init {
        viewModelScope.launch {
            combine(
                folderRepository.allFolders,
                noteRepository.allNotes,
                _sortOrder
            ) { folders, notes, sortOrder ->
                val sortedFolders = when (sortOrder) {
                    SortOrder.NAME -> folders.sortedBy { it.name.lowercase() }
                    SortOrder.DATE -> folders.sortedByDescending { it.updatedAt }
                }
                val sortedNotes = when (sortOrder) {
                    SortOrder.NAME -> notes.sortedBy { it.name.lowercase() }
                    SortOrder.DATE -> notes.sortedByDescending { it.updatedAt }
                }
                _state.value.copy(
                    folders = sortedFolders,
                    notes = sortedNotes,
                    isLoading = false,
                    sortOrder = sortOrder
                )
            }.collect { 
                _state.value = it
            }
        }
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun toggleFolder(folderId: Long) {
        val current = _state.value.expandedFolders
        _state.value = _state.value.copy(
            expandedFolders = if (current.contains(folderId)) current - folderId else current + folderId
        )
    }

    fun toggleNoteSelection(noteId: Long) {
        val current = _state.value.selectedNotes
        _state.value = _state.value.copy(
            selectedNotes = if (current.contains(noteId)) current - noteId else current + noteId
        )
    }
    
    fun toggleFolderSelection(folderId: Long) {
        val current = _state.value.selectedFolders
        _state.value = _state.value.copy(
            selectedFolders = if (current.contains(folderId)) current - folderId else current + folderId
        )
    }

    fun deleteSelected() {
        viewModelScope.launch {
            _state.value.selectedNotes.forEach { id ->
                _state.value.notes.find { it.id == id }?.let { noteRepository.delete(it) }
            }
            _state.value.selectedFolders.forEach { id ->
                _state.value.folders.find { it.id == id }?.let { folderRepository.delete(it) }
            }
            _state.value = _state.value.copy(selectedNotes = emptySet(), selectedFolders = emptySet())
        }
    }

    fun moveSelectedTo(targetFolderId: Long?) {
        viewModelScope.launch {
            _state.value.selectedNotes.forEach { id ->
                _state.value.notes.find { it.id == id }?.let { noteRepository.update(it.copy(folderId = targetFolderId ?: 0L)) }
            }
            _state.value.selectedFolders.forEach { id ->
                if (id != targetFolderId) {
                    _state.value.folders.find { it.id == id }?.let { folderRepository.update(it.copy(parentId = targetFolderId)) }
                }
            }
            _state.value = _state.value.copy(selectedNotes = emptySet(), selectedFolders = emptySet())
        }
    }

    fun createFolder(name: String, parentId: Long?) {
        viewModelScope.launch {
            folderRepository.insert(Folder(name = name, parentId = parentId))
        }
    }

    fun createNote(name: String, folderId: Long) {
        viewModelScope.launch {
            noteRepository.insert(Note(name = name, folderId = folderId))
        }
    }
    
    fun renameNote(note: Note, newName: String) {
        viewModelScope.launch {
            noteRepository.update(note.copy(name = newName))
        }
    }
    
    fun renameFolder(folder: Folder, newName: String) {
        viewModelScope.launch {
            folderRepository.update(folder.copy(name = newName))
        }
    }
    
    fun deleteNote(note: Note) {
        viewModelScope.launch {
            noteRepository.delete(note)
        }
    }
    
    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            folderRepository.delete(folder)
        }
    }

    fun exportNoteToPdf(context: android.content.Context, note: Note, onComplete: (File) -> Unit) {
        viewModelScope.launch {
            val folders = _state.value.folders
            val pathNames = mutableListOf<String>()
            var currentFolderId = note.folderId
            while (currentFolderId != 0L) {
                val folder = folders.find { it.id == currentFolderId }
                if (folder != null) {
                    pathNames.add(0, folder.name)
                    currentFolderId = folder.parentId ?: 0L
                } else {
                    break
                }
            }
            
            val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val namePrefix = if (pathNames.isNotEmpty()) pathNames.joinToString("-") + "-" else ""
            val fileName = "${namePrefix}${dateStr}-${note.name}.pdf"
            
            val exportDir = File(context.getExternalFilesDir(null), "Exports")
            if (!exportDir.exists()) exportDir.mkdirs()
            val outputFile = File(exportDir, fileName)
            
            val result = PdfExporter(context).exportNote(note, "", outputFile)
            if (result.isSuccess) {
                onComplete(outputFile)
            }
        }
    }
}

enum class CreateDialogType {
    FOLDER, NOTE, RENAME_NOTE, RENAME_FOLDER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(
    onNoteClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: FolderListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var dialogType by remember { mutableStateOf(CreateDialogType.NOTE) }
    var newItemName by remember { mutableStateOf("") }
    var targetFolderId by remember { mutableStateOf<Long?>(null) }
    var itemToRename by remember { mutableStateOf<Any?>(null) }
    
    var showMoveDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val folderBounds = remember { mutableStateMapOf<Long, Rect>() }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("My Notes") },
                actions = {
                    if (state.selectedNotes.isNotEmpty() || state.selectedFolders.isNotEmpty()) {
                        IconButton(onClick = { showMoveDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move Selected")
                        }
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    }
                    
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sort by Name") },
                                onClick = {
                                    viewModel.setSortOrder(SortOrder.NAME)
                                    showSortMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Date") },
                                onClick = {
                                    viewModel.setSortOrder(SortOrder.DATE)
                                    showSortMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) }
                            )
                        }
                    }

                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = {
                        dialogType = CreateDialogType.FOLDER
                        targetFolderId = null
                        newItemName = ""
                        showDialog = true
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "New Root Folder")
                }
                FloatingActionButton(
                    onClick = {
                        dialogType = CreateDialogType.NOTE
                        targetFolderId = 0L
                        newItemName = ""
                        showDialog = true
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "New Root Note")
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                item {
                    Text(
                        "All Files",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                renderTree(
                    folders = state.folders,
                    notes = state.notes,
                    parentId = null,
                    level = 0,
                    state = state,
                    viewModel = viewModel,
                    onNoteClick = onNoteClick,
                    dateFormat = dateFormat,
                    onDialogRequest = { type, pid, item ->
                        dialogType = type
                        targetFolderId = pid
                        itemToRename = item
                        newItemName = when (item) {
                            is Note -> item.name
                            is Folder -> item.name
                            else -> ""
                        }
                        showDialog = true
                    },
                    onUpdateFolderBounds = { id, rect -> folderBounds[id] = rect },
                    onDrop = { pos, note, folder ->
                        val targetId = folderBounds.entries.find { it.value.contains(pos) }?.key
                        if (note != null) {
                            if (!state.selectedNotes.contains(note.id)) {
                                viewModel.toggleNoteSelection(note.id)
                            }
                            viewModel.moveSelectedTo(targetId)
                        } else if (folder != null) {
                            if (!state.selectedFolders.contains(folder.id)) {
                                viewModel.toggleFolderSelection(folder.id)
                            }
                            viewModel.moveSelectedTo(targetId)
                        }
                    },
                    onExportPdf = { note ->
                        viewModel.exportNoteToPdf(context, note) { file ->
                            scope.launch {
                                snackbarHostState.showSnackbar("Exported to ${file.absolutePath}")
                            }
                        }
                    }
                )
                
                if (state.folders.isEmpty() && state.notes.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No folders or notes yet.")
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { 
                Text(when(dialogType) {
                    CreateDialogType.FOLDER -> "New Folder"
                    CreateDialogType.NOTE -> "New Note"
                    CreateDialogType.RENAME_NOTE, CreateDialogType.RENAME_FOLDER -> "Rename"
                }) 
            },
            text = {
                OutlinedTextField(
                    value = newItemName,
                    onValueChange = { newItemName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newItemName.isNotBlank()) {
                            when (dialogType) {
                                CreateDialogType.FOLDER -> viewModel.createFolder(newItemName, targetFolderId)
                                CreateDialogType.NOTE -> viewModel.createNote(newItemName, targetFolderId ?: 0L)
                                CreateDialogType.RENAME_NOTE -> (itemToRename as? Note)?.let { viewModel.renameNote(it, newItemName) }
                                CreateDialogType.RENAME_FOLDER -> (itemToRename as? Folder)?.let { viewModel.renameFolder(it, newItemName) }
                            }
                            showDialog = false
                        }
                    }
                ) {
                    Text(if (dialogType == CreateDialogType.RENAME_NOTE || dialogType == CreateDialogType.RENAME_FOLDER) "Rename" else "Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showMoveDialog) {
        MoveDialog(
            folders = state.folders,
            onDismiss = { showMoveDialog = false },
            onMove = { targetId ->
                viewModel.moveSelectedTo(targetId)
                showMoveDialog = false
            }
        )
    }
}

@Composable
fun MoveDialog(
    folders: List<Folder>,
    onDismiss: () -> Unit,
    onMove: (Long?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Folder") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("Root") },
                        modifier = Modifier.clickable { onMove(null) },
                        leadingContent = { Icon(Icons.Default.Home, contentDescription = null) }
                    )
                }
                items(folders) { folder ->
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        modifier = Modifier.clickable { onMove(folder.id) },
                        leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun LazyListScope.renderTree(
    folders: List<Folder>,
    notes: List<Note>,
    parentId: Long?,
    level: Int,
    state: FolderListState,
    viewModel: FolderListViewModel,
    onNoteClick: (Long) -> Unit,
    dateFormat: SimpleDateFormat,
    onDialogRequest: (CreateDialogType, Long?, Any?) -> Unit,
    onUpdateFolderBounds: (Long, Rect) -> Unit,
    onDrop: (Offset, Note?, Folder?) -> Unit,
    onExportPdf: (Note) -> Unit
) {
    val currentFolders = folders.filter { it.parentId == parentId }
    val currentNotes = notes.filter { if (parentId == null) it.folderId == 0L else it.folderId == parentId }

    currentFolders.forEach { folder ->
        val isExpanded = state.expandedFolders.contains(folder.id)
        val isSelected = state.selectedFolders.contains(folder.id)

        item(key = "f_${folder.id}") {
            FolderItem(
                folder = folder,
                level = level,
                isExpanded = isExpanded,
                isSelected = isSelected,
                onToggleExpand = { viewModel.toggleFolder(folder.id) },
                onToggleSelection = { viewModel.toggleFolderSelection(folder.id) },
                onCreateNote = { onDialogRequest(CreateDialogType.NOTE, folder.id, null) },
                onCreateSubfolder = { onDialogRequest(CreateDialogType.FOLDER, folder.id, null) },
                onRename = { onDialogRequest(CreateDialogType.RENAME_FOLDER, null, folder) },
                onDelete = { viewModel.deleteFolder(folder) },
                onUpdateBounds = { onUpdateFolderBounds(folder.id, it) },
                onDrop = { onDrop(it, null, folder) }
            )
        }

        if (isExpanded) {
            renderTree(
                folders = folders,
                notes = notes,
                parentId = folder.id,
                level = level + 1,
                state = state,
                viewModel = viewModel,
                onNoteClick = onNoteClick,
                dateFormat = dateFormat,
                onDialogRequest = onDialogRequest,
                onUpdateFolderBounds = onUpdateFolderBounds,
                onDrop = onDrop,
                onExportPdf = onExportPdf
            )
        }
    }

    items(currentNotes, key = { "n_${it.id}" }) { note ->
        NoteItem(
            note = note,
            level = level,
            isSelected = state.selectedNotes.contains(note.id),
            onToggleSelection = { viewModel.toggleNoteSelection(note.id) },
            onNoteClick = { onNoteClick(note.id) },
            dateFormat = dateFormat,
            onRename = { onDialogRequest(CreateDialogType.RENAME_NOTE, null, note) },
            onDelete = { viewModel.deleteNote(note) },
            onDrop = { onDrop(it, note, null) },
            onExportPdf = { onExportPdf(note) }
        )
    }
}

@Composable
fun FolderItem(
    folder: Folder,
    level: Int,
    isExpanded: Boolean,
    isSelected: Boolean,
    onToggleExpand: () -> Unit,
    onToggleSelection: () -> Unit,
    onCreateNote: () -> Unit,
    onCreateSubfolder: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onUpdateBounds: (Rect) -> Unit,
    onDrop: (Offset) -> Unit
) {
    var offsetY by remember { mutableFloatStateOf(0f) }
    var itemPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var itemSize by remember { mutableStateOf(IntSize.Zero) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                onUpdateBounds(coords.boundsInRoot())
                itemPositionInRoot = coords.positionInRoot()
                itemSize = coords.size
            }
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragEnd = {
                        val center = itemPositionInRoot + Offset(itemSize.width / 2f, itemSize.height / 2f + offsetY)
                        onDrop(center)
                        offsetY = 0f
                    },
                    onDragCancel = { offsetY = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetY += dragAmount.y
                    }
                )
            }
            .clickable { onToggleExpand() },
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(start = (level * 16 + 8).dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() })
            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
            Icon(
                if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                folder.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onCreateNote) {
                Icon(Icons.Default.Add, contentDescription = "Add Note", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onCreateSubfolder) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "Add Subfolder", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun NoteItem(
    note: Note,
    level: Int,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onNoteClick: () -> Unit,
    dateFormat: SimpleDateFormat,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDrop: (Offset) -> Unit,
    onExportPdf: () -> Unit
) {
    var offsetY by remember { mutableFloatStateOf(0f) }
    var itemPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var itemSize by remember { mutableStateOf(IntSize.Zero) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                itemPositionInRoot = coords.positionInRoot()
                itemSize = coords.size
            }
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragEnd = { 
                        val center = itemPositionInRoot + Offset(itemSize.width / 2f, itemSize.height / 2f + offsetY)
                        onDrop(center)
                        offsetY = 0f 
                    },
                    onDragCancel = { offsetY = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetY += dragAmount.y
                    }
                )
            }
            .clickable { onNoteClick() },
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(start = (level * 16 + 8).dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() })
            Spacer(modifier = Modifier.width(24.dp))
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(note.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${(note.content.length / 1024.0).format(2)} KB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(" • ", color = MaterialTheme.colorScheme.outline)
                    Text(
                        dateFormat.format(Date(note.updatedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(" • ", color = MaterialTheme.colorScheme.outline)
                    Text(
                        note.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onExportPdf) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)
