package uk.kayalab.mynotes.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import uk.kayalab.mynotes.data.PageTemplate
import uk.kayalab.mynotes.data.StylusButtonAction
import uk.kayalab.mynotes.export.toReadablePath

private val fontChoices = listOf("Default", "Serif", "SansSerif", "Monospace")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val defaultFontFamily by viewModel.defaultFontFamily.collectAsState()
    val exportFolderUri by viewModel.exportFolderUri.collectAsState()
    val stylusConfig by viewModel.stylusConfig.collectAsState()
    val defaultTemplate by viewModel.defaultTemplate.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        uri?.let(viewModel::backupTo)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::restoreFrom)
    }
    val currentIsDark = isDarkTheme ?: isSystemInDarkTheme()
    var showFontDialog by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setExportFolderUri(it.toString())
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
        ) {
            item { SectionTitle("Stylus") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        StylusActionRow(
                            title = "Primary button",
                            subtitle = "The button nearest the tip on most pens",
                            selected = stylusConfig.primaryButton,
                            onSelected = viewModel::setStylusPrimaryAction
                        )
                        StylusActionRow(
                            title = "Secondary button",
                            subtitle = "Only on pens with two buttons",
                            selected = stylusConfig.secondaryButton,
                            onSelected = viewModel::setStylusSecondaryAction
                        )
                        ListItem(
                            headlineContent = { Text("Stylus only") },
                            supportingContent = { Text("Fingers pan and zoom instead of drawing") },
                            trailingContent = {
                                Switch(checked = stylusConfig.stylusOnly, onCheckedChange = viewModel::setStylusOnly)
                            }
                        )
                    }
                }
            }

            item { SectionTitle("Export") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("PDF export folder", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = exportFolderUri?.let { Uri.parse(it).toReadablePath() }
                                ?: "No folder chosen. Exports go to app storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tip: choose a folder inside the Google Drive or OneDrive app and exported PDFs appear on your Mac automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { folderPickerLauncher.launch(null) }) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                                Text("Choose folder")
                            }
                            if (exportFolderUri != null) {
                                TextButton(onClick = { viewModel.setExportFolderUri(null) }) { Text("Clear") }
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Backup") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("All notes and folders", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "A zip you can keep anywhere. Restoring adds the notes it contains; it never deletes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { backupLauncher.launch(viewModel.backupFileName()) }, enabled = !busy) { Text("Back up") }
                            TextButton(onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !busy) { Text("Restore") }
                            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }

            item { SectionTitle("Appearance") }
            item {
                ListItem(
                    headlineContent = { Text("Dark theme") },
                    supportingContent = {
                        Text(
                            when (isDarkTheme) {
                                null -> "Follows the system"
                                true -> "On"
                                false -> "Off"
                            }
                        )
                    },
                    trailingContent = { Switch(checked = currentIsDark, onCheckedChange = { viewModel.setDarkTheme(it) }) }
                )
                if (isDarkTheme != null) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(onClick = { viewModel.setDarkTheme(null) }) { Text("Follow the system") }
                    }
                }
                ListItem(
                    headlineContent = { Text("Paper for new notes") },
                    supportingContent = { Text(defaultTemplate.label) },
                    trailingContent = {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { expanded = true }) { Text("Change") }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                PageTemplate.entries.forEach { template ->
                                    DropdownMenuItem(
                                        text = { Text(template.label) },
                                        onClick = {
                                            viewModel.setDefaultTemplate(template)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text("Default text font") },
                    supportingContent = { Text(defaultFontFamily) },
                    trailingContent = { TextButton(onClick = { showFontDialog = true }) { Text("Change") } }
                )
            }
        }
    }

    if (showFontDialog) {
        AlertDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text("Default text font") },
            text = {
                Column {
                    fontChoices.forEach { font ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = defaultFontFamily == font,
                                onClick = {
                                    viewModel.setDefaultFontFamily(font)
                                    showFontDialog = false
                                }
                            )
                            Text(font, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFontDialog = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun StylusActionRow(
    title: String,
    subtitle: String,
    selected: StylusButtonAction,
    onSelected: (StylusButtonAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) { Text(selected.label) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    StylusButtonAction.entries.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            onClick = {
                                onSelected(action)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}
