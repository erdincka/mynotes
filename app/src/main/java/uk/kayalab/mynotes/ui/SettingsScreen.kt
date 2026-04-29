package uk.kayalab.mynotes.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val defaultFontFamily by viewModel.defaultFontFamily.collectAsState()
    val exportFolderUri by viewModel.exportFolderUri.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val context = LocalContext.current

    val systemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val currentIsDark = isDarkTheme ?: systemInDarkTheme

    var showFontDialog by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setExportFolderUri(it.toString())
        }
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            item {
                Text("General Settings", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Local Export Folder
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Local Export Folder", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = exportFolderUri?.let { uriStr ->
                                runCatching { android.net.Uri.parse(uriStr).toReadablePath() }
                                    .getOrDefault("Custom folder configured")
                            } ?: "No folder selected — exports saved to app storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { folderPickerLauncher.launch(null) }) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(end = 4.dp)
                                )
                                Text("Choose Folder")
                            }
                            if (exportFolderUri != null) {
                                TextButton(onClick = { viewModel.setExportFolderUri(null) }) {
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }
            }

            // Theme
            item {
                Text(
                    "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
                ListItem(
                    headlineContent = { Text("Dark Theme") },
                    supportingContent = {
                        Text(if (isDarkTheme == null) "System Default" else if (isDarkTheme == true) "On" else "Off")
                    },
                    trailingContent = {
                        Switch(checked = currentIsDark, onCheckedChange = { viewModel.setDarkTheme(it) })
                    }
                )
                if (isDarkTheme != null) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(onClick = { viewModel.setDarkTheme(null) }) {
                            Text("Reset to System Default")
                        }
                    }
                }
            }

            // Font
            item {
                ListItem(
                    headlineContent = { Text("Default Font") },
                    supportingContent = { Text(defaultFontFamily) },
                    trailingContent = {
                        TextButton(onClick = { showFontDialog = true }) { Text("Change") }
                    }
                )
            }
        }
    }

    if (showFontDialog) {
        AlertDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text("Select Default Font") },
            text = {
                Column {
                    listOf("Default", "Serif", "SansSerif", "Monospace").forEach { font ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
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
            confirmButton = {}
        )
    }
}
