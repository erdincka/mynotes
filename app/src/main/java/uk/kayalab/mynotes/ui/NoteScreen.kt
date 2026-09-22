package uk.kayalab.mynotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import uk.kayalab.mynotes.data.PageTemplate
import uk.kayalab.mynotes.ui.canvas.CanvasScreen
import uk.kayalab.mynotes.ui.canvas.CanvasTool
import uk.kayalab.mynotes.ui.canvas.CanvasToolbar

private val defaultToolWidths = mapOf(
    CanvasTool.PEN to 5f,
    CanvasTool.BRUSH to 8f,
    CanvasTool.ERASER to 60f,
    CanvasTool.HIGHLIGHTER to 25f,
    CanvasTool.LASSO to 1f,
    CanvasTool.TEXT to 1f
)

@Composable
fun NoteScreen(
    noteId: Long,
    onBack: () -> Unit,
    viewModel: NoteViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val defaultFontFamily by settingsViewModel.defaultFontFamily.collectAsState()
    val stylusConfig by settingsViewModel.stylusConfig.collectAsState()
    val loadState by viewModel.loadState.collectAsState()
    val note by viewModel.note.collectAsState()
    val template = PageTemplate.fromName(note?.template)
    val isDirty by viewModel.isDirty.collectAsState()
    val message by viewModel.message.collectAsState()

    var currentTool by remember { mutableStateOf(CanvasTool.PEN) }
    var currentColor by remember { mutableStateOf(Color.Black) }
    val toolWidths = remember { mutableStateMapOf<CanvasTool, Float>().apply { putAll(defaultToolWidths) } }
    var currentFontSize by remember { mutableStateOf(40f) }
    var currentFontFamily by remember { mutableStateOf("Default") }

    LaunchedEffect(defaultFontFamily) {
        if (currentFontFamily == "Default") currentFontFamily = defaultFontFamily
    }

    LaunchedEffect(noteId) { viewModel.loadNote(noteId) }

    // Saves on navigation away and when the app goes to the background; both are cheap no-ops when clean.
    DisposableEffect(Unit) { onDispose { viewModel.saveNow() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveNow() }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(loadState) {
        when (loadState) {
            is NoteLoadState.Failed ->
                snackbarHostState.showSnackbar("This note could not be loaded, so editing is disabled.")
            NoteLoadState.Missing -> snackbarHostState.showSnackbar("This note no longer exists.")
            else -> Unit
        }
    }

    val handleBack = {
        viewModel.saveNow()
        onBack()
    }
    BackHandler(onBack = handleBack)

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            CanvasScreen(
                viewModel = viewModel,
                currentTool = currentTool,
                currentColor = currentColor,
                toolWidths = toolWidths,
                currentFontSize = currentFontSize,
                currentFontFamily = currentFontFamily,
                stylusConfig = stylusConfig,
                template = template,
                modifier = Modifier.fillMaxSize()
            )

            CanvasToolbar(
                currentTool = currentTool,
                onToolSelected = { currentTool = it },
                currentColor = currentColor,
                onColorSelected = { currentColor = it },
                currentStrokeWidth = toolWidths[currentTool] ?: 5f,
                onStrokeWidthChanged = { toolWidths[currentTool] = it },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onBack = handleBack,
                onShare = { viewModel.sharePdf() },
                onExport = { viewModel.exportPdf() },
                isDirty = isDirty,
                currentFontSize = currentFontSize,
                onFontSizeChanged = { currentFontSize = it },
                currentFontFamily = currentFontFamily,
                onFontFamilyChanged = { currentFontFamily = it },
                template = template,
                onTemplateSelected = viewModel::setTemplate,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
