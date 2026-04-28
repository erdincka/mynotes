package com.mynotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.mynotes.ui.SettingsViewModel
import com.mynotes.ui.canvas.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    noteId: Long,
    onBack: () -> Unit,
    viewModel: NoteViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val defaultFontFamily by settingsViewModel.defaultFontFamily.collectAsState()
    
    var currentTool by remember { mutableStateOf(CanvasTool.PEN) }
    var currentColor by remember { mutableStateOf(Color.Black) }
    var currentStrokeWidth by remember { mutableFloatStateOf(5f) }
    var previousStrokeWidth by remember { mutableFloatStateOf(5f) }
    
    var currentFontSize by remember { mutableFloatStateOf(32f) }
    var currentFontFamily by remember { mutableStateOf("Default") }

    // Initialize font family from settings if it's still Default
    LaunchedEffect(defaultFontFamily) {
        if (currentFontFamily == "Default") {
            currentFontFamily = defaultFontFamily
        }
    }
    
    val strokes by viewModel.strokes.collectAsState()
    var lastSavedStrokes by remember { mutableStateOf<List<StrokeData>?>(null) }
    
    // Initialize lastSavedStrokes when strokes are first loaded from DB
    LaunchedEffect(strokes) {
        if (lastSavedStrokes == null && strokes.isNotEmpty()) {
            lastSavedStrokes = strokes
        }
    }

    // Auto-save 3 seconds after last stroke change
    LaunchedEffect(strokes) {
        if (lastSavedStrokes != null && strokes != lastSavedStrokes) {
            delay(3000)
            viewModel.saveNote()
            lastSavedStrokes = strokes
        }
    }

    // Always save when leaving the screen so exports are up-to-date
    DisposableEffect(Unit) {
        onDispose { viewModel.saveNote() }
    }

    val hasUnsavedChanges = remember(strokes, lastSavedStrokes) {
        lastSavedStrokes != null && strokes != lastSavedStrokes
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showExitDialog by remember { mutableStateOf(false) }

    val handleBack = {
        if (hasUnsavedChanges) {
            showExitDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(onBack = handleBack)

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Do you want to save before leaving?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveNote()
                    lastSavedStrokes = strokes
                    showExitDialog = false
                    onBack()
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showExitDialog = false
                        onBack()
                    }) { Text("Discard") }
                    TextButton(onClick = {
                        showExitDialog = false
                    }) { Text("Cancel") }
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            CanvasScreen(
                noteId = noteId,
                viewModel = viewModel,
                currentTool = currentTool,
                currentColor = currentColor,
                currentStrokeWidth = currentStrokeWidth,
                currentFontSize = currentFontSize,
                currentFontFamily = currentFontFamily,
                modifier = Modifier.fillMaxSize()
            )

            CanvasToolbar(
                currentTool = currentTool,
                onToolSelected = { selectedTool ->
                    if (selectedTool == CanvasTool.HIGHLIGHTER && currentTool != CanvasTool.HIGHLIGHTER) {
                        previousStrokeWidth = currentStrokeWidth
                        currentStrokeWidth = 25f
                    } else if (selectedTool == CanvasTool.ERASER && currentTool != CanvasTool.ERASER) {
                        previousStrokeWidth = currentStrokeWidth
                        currentStrokeWidth = 25f
                    } else if (currentTool == CanvasTool.HIGHLIGHTER && selectedTool != CanvasTool.HIGHLIGHTER) {
                        currentStrokeWidth = previousStrokeWidth
                    } else if (currentTool == CanvasTool.ERASER && selectedTool != CanvasTool.ERASER) {
                        currentStrokeWidth = previousStrokeWidth
                    }
                    currentTool = selectedTool
                },
                currentColor = currentColor,
                onColorSelected = { currentColor = it },
                currentStrokeWidth = currentStrokeWidth,
                onStrokeWidthChanged = { currentStrokeWidth = it },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onBack = handleBack,
                onSave = { 
                    viewModel.saveNote()
                    lastSavedStrokes = strokes
                    scope.launch {
                        snackbarHostState.showSnackbar("Note saved successfully")
                    }
                },
                currentFontSize = currentFontSize,
                onFontSizeChanged = { currentFontSize = it },
                currentFontFamily = currentFontFamily,
                onFontFamilyChanged = { currentFontFamily = it },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
