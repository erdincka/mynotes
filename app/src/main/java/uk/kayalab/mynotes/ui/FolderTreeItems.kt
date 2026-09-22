package uk.kayalab.mynotes.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.kayalab.mynotes.data.Folder
import uk.kayalab.mynotes.data.FolderTree
import uk.kayalab.mynotes.data.NoteSummary
import java.text.SimpleDateFormat
import java.util.Date

data class MenuAction(val label: String, val onClick: () -> Unit)

@Composable
fun FolderItem(
    folder: Folder,
    level: Int,
    isExpanded: Boolean,
    isSelected: Boolean,
    pathLabel: String?,
    onToggleExpand: () -> Unit,
    onToggleSelection: () -> Unit,
    actions: List<MenuAction>
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onToggleExpand() },
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(start = (level * 16 + 8).dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                if (!pathLabel.isNullOrEmpty()) {
                    Text(pathLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            OverflowMenu(actions)
        }
    }
}

@Composable
fun NoteItem(
    note: NoteSummary,
    level: Int,
    isSelected: Boolean,
    pathLabel: String?,
    dateFormat: SimpleDateFormat,
    onToggleSelection: () -> Unit,
    onNoteClick: () -> Unit,
    actions: List<MenuAction>
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onNoteClick() },
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(start = (level * 16 + 8).dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() })
            Spacer(modifier = Modifier.width(8.dp))
            NoteThumbnail(note.thumbnail)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(note.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                val meta = buildString {
                    append(dateFormat.format(Date(note.updatedAt)))
                    append("  •  ")
                    append(if (note.strokeCount == 1) "1 stroke" else "${note.strokeCount} strokes")
                    if (!pathLabel.isNullOrEmpty()) append("  •  ").append(pathLabel)
                }
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            OverflowMenu(actions)
        }
    }
}

@Composable
private fun OverflowMenu(actions: List<MenuAction>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More actions")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        expanded = false
                        action.onClick()
                    }
                )
            }
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MoveDialog(
    folders: List<Folder>,
    isAllowed: (Long?) -> Boolean,
    onDismiss: () -> Unit,
    onMove: (Long?) -> Unit
) {
    val ordered = remember(folders) { flattenTree(folders) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("Top level") },
                        leadingContent = { Icon(Icons.Default.Home, contentDescription = null) },
                        modifier = Modifier.clickable(enabled = isAllowed(null)) { onMove(null) }
                    )
                }
                items(ordered.size) { index ->
                    val (folder, depth) = ordered[index]
                    val allowed = isAllowed(folder.id)
                    ListItem(
                        headlineContent = {
                            Text(
                                folder.name,
                                color = if (allowed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                            )
                        },
                        leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                        modifier = Modifier
                            .padding(start = (depth * 16).dp)
                            .clickable(enabled = allowed) { onMove(folder.id) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun DeleteConfirmDialog(request: DeleteRequest, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val summary = buildList {
        if (request.folderCount > 0) add(plural(request.folderCount, "folder"))
        if (request.noteCount > 0) add(plural(request.noteCount, "note"))
    }.joinToString(" and ")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${request.title}?") },
        text = { Text("$summary will be removed permanently. This cannot be undone.") },
        confirmButton = { Button(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"

/** Depth-first order with nesting depth, for indented pickers. */
fun flattenTree(folders: List<Folder>): List<Pair<Folder, Int>> {
    val out = ArrayList<Pair<Folder, Int>>()
    val visited = HashSet<Long>()
    fun walk(parentId: Long?, depth: Int) {
        for (folder in FolderTree.childrenOf(folders, parentId).sortedBy { it.name.lowercase() }) {
            if (!visited.add(folder.id)) continue
            out.add(folder to depth)
            walk(folder.id, depth + 1)
        }
    }
    walk(null, 0)
    return out
}

@Composable
private fun NoteThumbnail(bytes: ByteArray?) {
    val bitmap = remember(bytes) {
        bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 48.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        }
    }
}
