package uk.kayalab.mynotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import uk.kayalab.mynotes.data.Folder
import uk.kayalab.mynotes.data.FolderTree
import uk.kayalab.mynotes.data.NoteSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface Dialog {
    data class NewFolder(val parentId: Long?) : Dialog
    data class NewNote(val folderId: Long) : Dialog
    data class RenameNote(val note: NoteSummary) : Dialog
    data class RenameFolder(val folder: Folder) : Dialog
    data object Move : Dialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(
    onNoteClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: FolderListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val allFolders by viewModel.allFolders.collectAsState()
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy HH:mm", Locale.UK) }
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = state.hasSelection) { viewModel.clearSelection() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (state.hasSelection) "${state.selectedNotes.size + state.selectedFolders.size} selected" else "My Notes") },
                    actions = {
                        if (state.hasSelection) {
                            IconButton(onClick = { dialog = Dialog.Move }) {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move selected")
                            }
                            IconButton(onClick = { viewModel.requestDeleteSelection() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                            }
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear selection")
                            }
                        }
                        IconButton(onClick = { dialog = Dialog.NewFolder(null) }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder")
                        }
                        IconButton(onClick = {
                            searchActive = !searchActive
                            if (!searchActive) viewModel.setSearchQuery("")
                        }) {
                            Icon(if (searchActive) Icons.Default.SearchOff else Icons.Default.Search, contentDescription = "Search")
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Sort by name") },
                                    leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null) },
                                    onClick = { viewModel.setSortOrder(SortOrder.NAME); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort by date") },
                                    leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                                    onClick = { viewModel.setSortOrder(SortOrder.DATE); showSortMenu = false }
                                )
                            }
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
                if (searchActive) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = { Text("Search notes and folders") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = Dialog.NewNote(ROOT_FOLDER_ID) },
                icon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
                text = { Text("New note") }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            val context = ItemContext(
                state = state,
                allFolders = allFolders,
                viewModel = viewModel,
                dateFormat = dateFormat,
                onNoteClick = onNoteClick,
                openDialog = { dialog = it }
            )
            if (state.isSearching) {
                searchResults(context)
            } else {
                tree(context, parentId = null, level = 0)
            }
            if (state.folders.isEmpty() && state.notes.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.isSearching) "Nothing matches \"${state.searchQuery}\"." else "No notes yet. Tap New note to start. Long-press any item to select several.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    when (val current = dialog) {
        null -> Unit
        is Dialog.NewFolder -> NameDialog("New folder", "Create", "", onDismiss = { dialog = null }) {
            viewModel.createFolder(it, current.parentId)
            dialog = null
        }
        is Dialog.NewNote -> NameDialog("New note", "Create", defaultNoteName(), onDismiss = { dialog = null }) {
            viewModel.createNote(it, current.folderId, onCreated = onNoteClick)
            dialog = null
        }
        is Dialog.RenameNote -> NameDialog("Rename note", "Rename", current.note.name, onDismiss = { dialog = null }) {
            viewModel.renameNote(current.note.id, it)
            dialog = null
        }
        is Dialog.RenameFolder -> NameDialog("Rename folder", "Rename", current.folder.name, onDismiss = { dialog = null }) {
            viewModel.renameFolder(current.folder.id, it)
            dialog = null
        }
        Dialog.Move -> MoveDialog(
            folders = allFolders,
            isAllowed = { target -> state.selectedFolders.all { viewModel.canMoveFolder(it, target) } },
            onDismiss = { dialog = null },
            onMove = {
                viewModel.moveSelectedTo(it)
                dialog = null
            }
        )
    }

    state.pendingDelete?.let { request ->
        DeleteConfirmDialog(request, onDismiss = viewModel::cancelDelete, onConfirm = viewModel::confirmDelete)
    }
}

private fun defaultNoteName(): String =
    "Note " + SimpleDateFormat("d MMM yyyy HH:mm", Locale.UK).format(Date())

private class ItemContext(
    val state: FolderListState,
    val allFolders: List<Folder>,
    val viewModel: FolderListViewModel,
    val dateFormat: SimpleDateFormat,
    val onNoteClick: (Long) -> Unit,
    val openDialog: (Dialog) -> Unit
)

private fun LazyListScope.tree(ctx: ItemContext, parentId: Long?, level: Int) {
    val folders = ctx.state.folders.filter { it.parentId == parentId }
    val notes = ctx.state.notes.filter { it.folderId == (parentId ?: ROOT_FOLDER_ID) }

    folders.forEach { folder ->
        val isExpanded = folder.id in ctx.state.expandedFolders
        item(key = "f_${folder.id}") { folderRow(ctx, folder, level, isExpanded, pathLabel = null) }
        if (isExpanded) tree(ctx, folder.id, level + 1)
    }
    items(notes, key = { "n_${it.id}" }) { note -> noteRow(ctx, note, level, pathLabel = null) }
}

private fun LazyListScope.searchResults(ctx: ItemContext) {
    items(ctx.state.folders, key = { "f_${it.id}" }) { folder ->
        folderRow(ctx, folder, level = 0, isExpanded = false, pathLabel = FolderTree.pathLabel(ctx.allFolders, folder.parentId))
    }
    items(ctx.state.notes, key = { "n_${it.id}" }) { note ->
        noteRow(ctx, note, level = 0, pathLabel = FolderTree.pathLabel(ctx.allFolders, note.folderId).ifEmpty { "Top level" }, snippet = snippetFor(note.recognizedText, ctx.state.searchQuery))
    }
}

@Composable
private fun folderRow(ctx: ItemContext, folder: Folder, level: Int, isExpanded: Boolean, pathLabel: String?) {
    FolderItem(
        folder = folder,
        level = level,
        isExpanded = isExpanded,
        isSelected = folder.id in ctx.state.selectedFolders,
        selectionMode = ctx.state.hasSelection,
        pathLabel = pathLabel,
        onToggleExpand = {
            if (ctx.state.isSearching) ctx.viewModel.revealFolder(folder.id) else ctx.viewModel.toggleFolder(folder.id)
        },
        onToggleSelection = { ctx.viewModel.toggleFolderSelection(folder.id) },
        actions = listOf(
            MenuAction("New note here") { ctx.openDialog(Dialog.NewNote(folder.id)) },
            MenuAction("New subfolder") { ctx.openDialog(Dialog.NewFolder(folder.id)) },
            MenuAction("Rename") { ctx.openDialog(Dialog.RenameFolder(folder)) },
            MenuAction("Move") {
                if (folder.id !in ctx.state.selectedFolders) ctx.viewModel.toggleFolderSelection(folder.id)
                ctx.openDialog(Dialog.Move)
            },
            MenuAction("Delete") { ctx.viewModel.requestDeleteFolder(folder) }
        )
    )
}

/** The line of recognised text around the first match, so the hit is visible in the list. */
private fun snippetFor(text: String, query: String): String? {
    if (text.isBlank() || query.isBlank()) return null
    val index = text.indexOf(query, ignoreCase = true)
    if (index < 0) return null
    val start = (index - 40).coerceAtLeast(0)
    val end = (index + query.length + 60).coerceAtMost(text.length)
    return (if (start > 0) "…" else "") + text.substring(start, end).replace('\n', ' ') + (if (end < text.length) "…" else "")
}

@Composable
private fun noteRow(ctx: ItemContext, note: NoteSummary, level: Int, pathLabel: String?, snippet: String? = null) {
    NoteItem(
        snippet = snippet,
        note = note,
        level = level,
        isSelected = note.id in ctx.state.selectedNotes,
        selectionMode = ctx.state.hasSelection,
        pathLabel = pathLabel,
        dateFormat = ctx.dateFormat,
        onToggleSelection = { ctx.viewModel.toggleNoteSelection(note.id) },
        onNoteClick = { ctx.onNoteClick(note.id) },
        actions = listOf(
            MenuAction("Send as PDF") { ctx.viewModel.shareNoteAsPdf(note.id) },
            MenuAction("Export PDF to folder") { ctx.viewModel.exportNoteToPdf(note.id) },
            MenuAction("Rename") { ctx.openDialog(Dialog.RenameNote(note)) },
            MenuAction("Move") {
                if (note.id !in ctx.state.selectedNotes) ctx.viewModel.toggleNoteSelection(note.id)
                ctx.openDialog(Dialog.Move)
            },
            MenuAction("Delete") { ctx.viewModel.requestDeleteNote(note) }
        )
    )
}
