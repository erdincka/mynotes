package com.mynotes.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val onedriveAccessToken by viewModel.onedriveAccessToken.collectAsState()
    val onedriveFolderName by viewModel.onedriveFolderName.collectAsState()
    val oneDriveFolders by viewModel.oneDriveFolders.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()

    val context = LocalContext.current

    val systemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val currentIsDark = isDarkTheme ?: systemInDarkTheme

    var showFontDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var tempToken by remember { mutableStateOf("") }

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
            
            // OneDrive Connection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("OneDrive Sync", style = MaterialTheme.typography.titleMedium)
                        if (onedriveAccessToken == null) {
                            Text("Connect to your OneDrive to sync notes across devices.")
                            Spacer(modifier = Modifier.height(8.dp))
                            if (isConnecting) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Button(onClick = { 
                                    val oauthUrl = viewModel.getOAuthUrl()
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(oauthUrl))
                                    context.startActivity(intent)
                                }) {
                                    Text("Connect OneDrive")
                                }
                            }
                        } else {
                            Text("Connected to OneDrive")
                            Text("Target Folder: ${onedriveFolderName ?: "Not selected"}", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row {
                                Button(onClick = { viewModel.refreshFolders() }) {
                                    Text("Select Folder")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = { viewModel.disconnectOneDrive() }) {
                                    Text("Disconnect")
                                }
                            }
                        }
                    }
                }
            }

            if (onedriveAccessToken != null && oneDriveFolders.isNotEmpty()) {
                item {
                    Text("Select OneDrive Folder", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                }
                items(oneDriveFolders) { folder ->
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingContent = {
                            RadioButton(
                                selected = false, // We don't have the selected folder ID easily here to compare
                                onClick = { viewModel.selectOneDriveFolder(folder) }
                            )
                        }
                    )
                }
            }

            // Theme settings
            item {
                Text("Appearance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                ListItem(
                    headlineContent = { Text("Dark Theme") },
                    supportingContent = { 
                        Text(if (isDarkTheme == null) "System Default" else if (isDarkTheme == true) "On" else "Off")
                    },
                    trailingContent = { 
                        Switch(
                            checked = currentIsDark, 
                            onCheckedChange = { viewModel.setDarkTheme(it) }
                        ) 
                    }
                )
                if (isDarkTheme != null) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(
                            onClick = { viewModel.setDarkTheme(null) }
                        ) {
                            Text("Reset to System Default")
                        }
                    }
                }
            }

            // Font settings
            item {
                ListItem(
                    headlineContent = { Text("Default Font") },
                    supportingContent = { Text(defaultFontFamily) },
                    trailingContent = { 
                        TextButton(onClick = { showFontDialog = true }) { 
                            Text("Change") 
                        } 
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
            confirmButton = {}
        )
    }

    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("Connect OneDrive") },
            text = {
                Column {
                    Text("Please enter your OneDrive Access Token:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempToken,
                        onValueChange = { tempToken = it },
                        label = { Text("Access Token") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (tempToken.isNotBlank()) {
                        viewModel.connectOneDrive(tempToken)
                        showTokenDialog = false
                    }
                }) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
