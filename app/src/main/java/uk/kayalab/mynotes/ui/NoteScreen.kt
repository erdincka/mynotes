package uk.kayalab.mynotes.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import uk.kayalab.mynotes.ui.canvas.CanvasViewport
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import uk.kayalab.mynotes.ui.canvas.ShapeKind

private val defaultToolWidths = mapOf(
    CanvasTool.PEN to 5f,
    CanvasTool.BRUSH to 8f,
    CanvasTool.ERASER to 60f,
    CanvasTool.HIGHLIGHTER to 25f,
    CanvasTool.SHAPE to 5f,
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
    val message by viewModel.message.collectAsState()

    var currentTool by remember { mutableStateOf(CanvasTool.PEN) }
    var currentColor by remember { mutableStateOf(Color.Black) }
    val toolWidths = remember { mutableStateMapOf<CanvasTool, Float>().apply { putAll(defaultToolWidths) } }
    var currentFontSize by remember { mutableStateOf(40f) }
    var currentFontFamily by remember { mutableStateOf("Default") }
    var currentShape by remember { mutableStateOf(ShapeKind.RECTANGLE) }
    val viewport = remember { CanvasViewport() }
    val context = LocalContext.current
    val recognizedText by viewModel.recognizedText.collectAsState()
    val isRecognizing by viewModel.isRecognizing.collectAsState()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val maxWidth = viewport.visibleWidth * 0.6f
            val centre = viewport.visibleCentre
            viewModel.insertImage(uri, topLeft = centre - androidx.compose.ui.geometry.Offset(maxWidth / 2f, maxWidth / 3f), maxWidth = maxWidth)
        }
    }

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

    if (isRecognizing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Reading handwriting") },
            text = { Box(modifier = Modifier.fillMaxSize().heightIn(max = 48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
            confirmButton = {}
        )
    }
    recognizedText?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRecognizedText,
            title = { Text("Recognised text") },
            text = {
                Text(text, modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
            },
            confirmButton = {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("MyNotes", text))
                    viewModel.dismissRecognizedText()
                }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissRecognizedText) { Text("Close") } }
        )
    }

    // The canvas runs edge to edge; only the toolbar pads for the status bar, so it sits flush at the top.
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
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
                currentShape = currentShape,
                viewport = viewport,
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
                onInsertImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onCopyText = { viewModel.recognizeNow() },
                currentFontSize = currentFontSize,
                onFontSizeChanged = { currentFontSize = it },
                currentFontFamily = currentFontFamily,
                onFontFamilyChanged = { currentFontFamily = it },
                template = template,
                onTemplateSelected = viewModel::setTemplate,
                currentShape = currentShape,
                onShapeSelected = { currentShape = it },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
