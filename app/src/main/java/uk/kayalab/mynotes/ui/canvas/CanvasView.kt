package uk.kayalab.mynotes.ui.canvas

import android.graphics.Color as AndroidColor
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.withFrameNanos
import androidx.input.motionprediction.MotionEventPredictor
import uk.kayalab.mynotes.data.PageTemplate
import uk.kayalab.mynotes.data.StylusButtonAction
import uk.kayalab.mynotes.data.StylusConfig
import uk.kayalab.mynotes.export.PdfRenderer
import uk.kayalab.mynotes.ui.NoteLoadState
import uk.kayalab.mynotes.ui.NoteViewModel
import uk.kayalab.mynotes.ui.theme.LocalIsDarkTheme
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun CanvasScreen(
    viewModel: NoteViewModel,
    currentTool: CanvasTool,
    currentColor: Color,
    toolWidths: Map<CanvasTool, Float>,
    currentFontSize: Float,
    currentFontFamily: String,
    stylusConfig: StylusConfig,
    template: PageTemplate,
    modifier: Modifier = Modifier,
) {
    val strokes by viewModel.strokes.collectAsState()
    val selectedIds by viewModel.selectedStrokeIds.collectAsState()
    val loadState by viewModel.loadState.collectAsState()

    CanvasView(
        strokes = strokes,
        selectedIds = selectedIds,
        readOnly = loadState != NoteLoadState.Ready,
        onStrokeAdded = viewModel::addStroke,
        onEraserStart = viewModel::startErasing,
        onEraserAction = viewModel::eraseAt,
        onLassoComplete = viewModel::selectStrokesInPath,
        onSelectionMove = viewModel::moveSelectedStrokes,
        onClearSelection = viewModel::clearSelection,
        onUndo = viewModel::undo,
        currentTool = currentTool,
        currentColor = currentColor,
        toolWidths = toolWidths,
        currentFontSize = currentFontSize,
        currentFontFamily = currentFontFamily,
        stylusConfig = stylusConfig,
        template = template,
        modifier = modifier
    )
}

private class StrokeShape(val stroke: StrokeData, val path: Path, val kind: StrokeShapes.Kind)

private fun shapeFor(stroke: StrokeData): StrokeShape {
    val sink = ComposePathSink()
    val kind = StrokeShapes.emit(stroke, sink)
    return StrokeShape(stroke, sink.path, kind)
}

/** Committed strokes rendered once per change into a screen-sized bitmap, so a live stroke costs one blit. */
private class CommittedLayer {
    var bitmap: ImageBitmap? = null
    private var strokes: List<StrokeData>? = null
    private var selectedIds: Set<Long>? = null
    private var pan = Offset.Unspecified
    private var zoom = 0f
    private var dark = false
    private var template: PageTemplate? = null

    fun isCurrent(strokes: List<StrokeData>, selectedIds: Set<Long>, pan: Offset, zoom: Float, dark: Boolean, template: PageTemplate, width: Int, height: Int): Boolean =
        this.strokes === strokes && this.selectedIds === selectedIds && this.pan == pan && this.zoom == zoom &&
            this.dark == dark && this.template == template && bitmap?.width == width && bitmap?.height == height

    fun remember(strokes: List<StrokeData>, selectedIds: Set<Long>, pan: Offset, zoom: Float, dark: Boolean, template: PageTemplate) {
        this.strokes = strokes; this.selectedIds = selectedIds; this.pan = pan; this.zoom = zoom; this.dark = dark; this.template = template
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CanvasView(
    strokes: List<StrokeData>,
    selectedIds: Set<Long>,
    readOnly: Boolean,
    onStrokeAdded: (StrokeData) -> Unit,
    onEraserStart: () -> Unit,
    onEraserAction: (Offset, Float) -> Unit,
    onLassoComplete: (List<Offset>) -> Unit,
    onSelectionMove: (Offset) -> Unit,
    onClearSelection: () -> Unit,
    onUndo: () -> Unit,
    currentTool: CanvasTool,
    currentColor: Color,
    toolWidths: Map<CanvasTool, Float>,
    currentFontSize: Float,
    currentFontFamily: String,
    stylusConfig: StylusConfig,
    template: PageTemplate,
    modifier: Modifier = Modifier,
) {
    var panX by rememberSaveable { mutableFloatStateOf(0f) }
    var panY by rememberSaveable { mutableFloatStateOf(0f) }
    var zoomScale by rememberSaveable { mutableFloatStateOf(1f) }
    val panOffset = Offset(panX, panY)

    val currentStrokePoints = remember { mutableStateListOf<Offset>() }
    val currentPressures = remember { mutableStateListOf<Float>() }
    var activeTool by remember { mutableStateOf<CanvasTool?>(null) }
    var lassoPathPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectionOffset by remember { mutableStateOf(Offset.Zero) }

    val isDarkTheme = LocalIsDarkTheme.current
    val density = LocalDensity.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val textPaint = remember { android.graphics.Paint().apply { isAntiAlias = true } }
    val shapeCache = remember { HashMap<Long, StrokeShape>() }
    val committedLayer = remember { CommittedLayer() }
    val predictor = remember(view) { MotionEventPredictor.newInstance(view) }
    var predictedTail by remember { mutableStateOf<List<Offset>>(emptyList()) }
    // Spied MotionEvents are in the root view's space, not this canvas's, so predictions are used
    // only as a displacement from the last recorded sample and applied to the last real point.
    var lastRecordedScreenPos by remember { mutableStateOf<Offset?>(null) }

    // Values read inside the long-running gesture coroutine must come through state holders,
    // otherwise the coroutine keeps the values captured when pointerInput first ran.
    val strokesState = rememberUpdatedState(strokes)
    val selectedIdsState = rememberUpdatedState(selectedIds)
    val readOnlyState = rememberUpdatedState(readOnly)
    val toolState = rememberUpdatedState(currentTool)
    val colorState = rememberUpdatedState(currentColor)
    val widthsState = rememberUpdatedState(toolWidths)
    val fontSizeState = rememberUpdatedState(currentFontSize)
    val fontFamilyState = rememberUpdatedState(currentFontFamily)
    val stylusState = rememberUpdatedState(stylusConfig)
    val undoState = rememberUpdatedState(onUndo)
    val panState = rememberUpdatedState(panOffset)
    val zoomState = rememberUpdatedState(zoomScale)

    remember(strokes) {
        val ids = strokes.mapTo(HashSet()) { it.id }
        shapeCache.keys.retainAll(ids)
    }

    // A barrel button pressed while the pen hovers arrives as a generic motion event, never
    // through Compose pointer input. Only press-triggered actions are handled here; hold actions
    // are read from the button state at stroke start instead.
    DisposableEffect(view) {
        var previousButtons = 0
        val listener = View.OnGenericMotionListener { _, event ->
            val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                event.source and InputDevice.SOURCE_STYLUS != 0
            if (!isStylus) return@OnGenericMotionListener false
            val buttons = event.buttonState
            val pressed = buttons and previousButtons.inv()
            previousButtons = buttons
            val config = stylusState.value
            val undoPressed =
                (pressed and MotionEvent.BUTTON_STYLUS_PRIMARY != 0 && config.primaryButton == StylusButtonAction.UNDO) ||
                    (pressed and MotionEvent.BUTTON_STYLUS_SECONDARY != 0 && config.secondaryButton == StylusButtonAction.UNDO)
            if (undoPressed) undoState.value()
            undoPressed
        }
        view.setOnGenericMotionListener(listener)
        onDispose { view.setOnGenericMotionListener(null) }
    }

    var textPosition by remember { mutableStateOf<Offset?>(null) }
    var textValue by remember { mutableStateOf("") }

    val commitText = {
        val position = textPosition
        if (textValue.isNotEmpty() && position != null) {
            onStrokeAdded(
                StrokeData(
                    points = listOf(position),
                    color = "#%08X".format(currentColor.toArgb()),
                    strokeWidth = toolWidths[CanvasTool.TEXT] ?: 1f,
                    tool = "text",
                    text = textValue,
                    fontSize = currentFontSize,
                    fontFamily = currentFontFamily
                )
            )
        }
        textValue = ""
        textPosition = null
        focusManager.clearFocus()
    }

    LaunchedEffect(currentTool) {
        if (currentTool != CanvasTool.TEXT) commitText()
        if (currentTool != CanvasTool.LASSO) {
            lassoPathPoints = emptyList()
            onClearSelection()
        }
    }
    LaunchedEffect(textPosition) {
        if (textPosition != null) focusRequester.requestFocus()
    }

    // Once per frame while inking, ask the predictor where the pen is heading and draw that
    // tail ahead of the real samples. It is never stored.
    LaunchedEffect(activeTool) {
        val tool = activeTool
        if (tool == null || tool == CanvasTool.ERASER || tool == CanvasTool.LASSO) {
            predictedTail = emptyList()
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { }
            val predicted = predictor.predict()
            val anchorScreen = lastRecordedScreenPos
            val anchorContent = currentStrokePoints.lastOrNull()
            predictedTail = if (predicted == null || anchorScreen == null || anchorContent == null) emptyList() else {
                val zoom = zoomState.value
                val tail = ArrayList<Offset>(predicted.historySize + 1)
                for (h in 0 until predicted.historySize) {
                    val delta = Offset(predicted.getHistoricalX(h), predicted.getHistoricalY(h)) - anchorScreen
                    tail.add(anchorContent + delta / zoom)
                }
                tail.add(anchorContent + (Offset(predicted.x, predicted.y) - anchorScreen) / zoom)
                predicted.recycle()
                tail.filter { (it - anchorContent).getDistance() > 0.5f }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .motionEventSpy { event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            predictor.record(event)
                            lastRecordedScreenPos = Offset(event.x, event.y)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            predictor.record(event)
                            lastRecordedScreenPos = null
                        }
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        firstDown.consume()
                        val buttons = currentEvent.buttons
                        val pointerType = firstDown.type
                        val config = stylusState.value

                        val updatePanZoom = { pan: Offset, zoom: Float ->
                            panX = pan.x; panY = pan.y; zoomScale = zoom
                        }
                        val fingerShouldPan = config.stylusOnly && pointerType == PointerType.Touch
                        if (readOnlyState.value || fingerShouldPan) {
                            panUntilRelease(firstDown.id, { panState.value }, { zoomState.value }, updatePanZoom)
                            return@awaitEachGesture
                        }

                        val heldAction = heldButtonAction(buttons, config)
                        val effectiveTool = when {
                            pointerType == PointerType.Eraser -> CanvasTool.ERASER
                            heldAction == StylusButtonAction.ERASER -> CanvasTool.ERASER
                            heldAction == StylusButtonAction.LASSO -> CanvasTool.LASSO
                            heldAction == StylusButtonAction.HIGHLIGHTER -> CanvasTool.HIGHLIGHTER
                            else -> toolState.value
                        }
                        val toolWidth = widthsState.value[effectiveTool] ?: 5f
                        val startPos = (firstDown.position - panState.value) / zoomState.value

                        if (effectiveTool == CanvasTool.TEXT) {
                            if (textPosition != null) commitText()
                            textPosition = startPos
                            textValue = ""
                            return@awaitEachGesture
                        }

                        if (effectiveTool == CanvasTool.LASSO && selectedIdsState.value.isNotEmpty()) {
                            val touchedSelected = strokesState.value.any {
                                it.id in selectedIdsState.value && StrokeGeometry.isPointNearStroke(startPos, it)
                            }
                            if (touchedSelected) {
                                var lastPos = startPos
                                selectionOffset = Offset.Zero
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } > 1) break
                                    val change = event.changes.firstOrNull { it.id == firstDown.id }
                                    if (change != null && change.pressed) {
                                        val pos = (change.position - panState.value) / zoomState.value
                                        selectionOffset += pos - lastPos
                                        lastPos = pos
                                        change.consume()
                                    }
                                } while (event.changes.any { it.pressed && it.id == firstDown.id })
                                onSelectionMove(selectionOffset)
                                selectionOffset = Offset.Zero
                                lassoPathPoints = emptyList()
                                return@awaitEachGesture
                            }
                        }

                        if (effectiveTool == CanvasTool.LASSO) {
                            lassoPathPoints = emptyList()
                            onClearSelection()
                        }

                        activeTool = effectiveTool
                        currentStrokePoints.clear()
                        currentStrokePoints.add(startPos)
                        currentPressures.clear()
                        currentPressures.add(firstDown.pressure)

                        if (effectiveTool == CanvasTool.ERASER) {
                            onEraserStart()
                            onEraserAction(startPos, toolWidth / 2f / zoomState.value)
                        }

                        // While a pen is down, a resting palm must not count as a second pointer.
                        val stylusDown = pointerType == PointerType.Stylus || pointerType == PointerType.Eraser
                        fun countsAsPointer(type: PointerType) = !stylusDown || type != PointerType.Touch

                        var cancelled = false
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed && countsAsPointer(it.type) } >= 2) {
                                cancelled = true
                                currentStrokePoints.clear()
                                currentPressures.clear()
                                activeTool = null
                                zoomUntilRelease({ panState.value }, { zoomState.value }, updatePanZoom)
                                break
                            }
                            val change = event.changes.firstOrNull { it.id == firstDown.id }
                            if (change != null && change.pressed) {
                                val pos = (change.position - panState.value) / zoomState.value
                                currentStrokePoints.add(pos)
                                currentPressures.add(StrokeOutline.smoothPressure(currentPressures.lastOrNull(), change.pressure))
                                if (effectiveTool == CanvasTool.ERASER) {
                                    onEraserAction(pos, toolWidth / 2f / zoomState.value)
                                }
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed && it.id == firstDown.id })

                        if (!cancelled && currentStrokePoints.size > 1) {
                            when (effectiveTool) {
                                CanvasTool.LASSO -> {
                                    val closeThreshold = 80f / zoomState.value
                                    val first = currentStrokePoints.first()
                                    val isClosed = currentStrokePoints.size > 3 &&
                                        currentStrokePoints.takeLast(8).any { (it - first).getDistance() < closeThreshold }
                                    if (isClosed) {
                                        val snapshot = currentStrokePoints.toList()
                                        lassoPathPoints = snapshot
                                        onLassoComplete(snapshot)
                                    } else {
                                        lassoPathPoints = emptyList()
                                        onClearSelection()
                                    }
                                }
                                CanvasTool.ERASER -> Unit
                                else -> {
                                    val argb = colorState.value.toArgb()
                                    val colorHex = if (effectiveTool == CanvasTool.HIGHLIGHTER) {
                                        "#40%06X".format(argb and 0xFFFFFF)
                                    } else {
                                        "#%08X".format(argb)
                                    }
                                    onStrokeAdded(
                                        StrokeData(
                                            points = currentStrokePoints.toList(),
                                            pressures = currentPressures.toList(),
                                            color = colorHex,
                                            strokeWidth = toolWidth,
                                            tool = effectiveTool.name.lowercase(),
                                            fontSize = fontSizeState.value,
                                            fontFamily = fontFamilyState.value
                                        )
                                    )
                                }
                            }
                        }
                        activeTool = null
                        predictedTail = emptyList()
                        currentStrokePoints.clear()
                        currentPressures.clear()
                    }
                }
        ) {
            val width = size.width.roundToInt()
            val height = size.height.roundToInt()
            if (width > 0 && height > 0) {
                if (!committedLayer.isCurrent(strokes, selectedIds, panOffset, zoomScale, isDarkTheme, template, width, height)) {
                    val bitmap = committedLayer.bitmap?.takeIf { it.width == width && it.height == height }
                        ?: ImageBitmap(width, height).also { committedLayer.bitmap = it }
                    val layerCanvas = ComposeCanvas(bitmap)
                    layerCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { blendMode = BlendMode.Clear })
                    CanvasDrawScope().draw(this, layoutDirection, layerCanvas, Size(width.toFloat(), height.toFloat())) {
                        translate(left = panOffset.x, top = panOffset.y) {
                            scale(zoomScale, pivot = Offset.Zero) {
                                drawTemplate(template, panOffset, zoomScale)
                                strokes.forEach { stroke ->
                                    if (stroke.id in selectedIds) return@forEach
                                    val shape = shapeCache[stroke.id]?.takeIf { it.stroke === stroke }
                                        ?: shapeFor(stroke).also { shapeCache[stroke.id] = it }
                                    drawStroke(stroke, shape, isSelected = false, isDarkTheme, textPaint)
                                }
                            }
                        }
                    }
                    committedLayer.remember(strokes, selectedIds, panOffset, zoomScale, isDarkTheme, template)
                }
                committedLayer.bitmap?.let { drawImage(it) }
            }

            translate(left = panOffset.x, top = panOffset.y) {
                scale(zoomScale, pivot = Offset.Zero) {
                    if (selectedIds.isNotEmpty()) {
                        strokes.forEach { stroke ->
                            if (stroke.id !in selectedIds) return@forEach
                            val shape = shapeCache[stroke.id]?.takeIf { it.stroke === stroke }
                                ?: shapeFor(stroke).also { shapeCache[stroke.id] = it }
                            translate(selectionOffset.x, selectionOffset.y) {
                                drawStroke(stroke, shape, isSelected = true, isDarkTheme, textPaint)
                            }
                        }
                    }

                    val tool = activeTool
                    if (tool != null && currentStrokePoints.isNotEmpty()) {
                        val width = toolWidths[tool] ?: 5f
                        when (tool) {
                            CanvasTool.ERASER -> drawCircle(
                                color = Color.Gray.copy(alpha = 0.35f),
                                radius = width / 2f / zoomScale,
                                center = currentStrokePoints.last()
                            )
                            CanvasTool.LASSO -> drawPolyline(
                                currentStrokePoints,
                                Color.Blue.copy(alpha = 0.6f),
                                Stroke(width = 1f / zoomScale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                            )
                            else -> {
                                val base = if (tool == CanvasTool.HIGHLIGHTER) currentColor.copy(alpha = 0.25f) else currentColor
                                val color = if (isDarkTheme) invertColor(base) else base
                                val points = if (predictedTail.isEmpty()) currentStrokePoints.toList() else currentStrokePoints + predictedTail
                                val pressures = if (predictedTail.isEmpty()) currentPressures.toList() else {
                                    val last = currentPressures.lastOrNull() ?: 1f
                                    currentPressures + List(predictedTail.size) { last }
                                }
                                val sink = ComposePathSink()
                                val kind = StrokeShapes.emit(points, pressures, tool.name.lowercase(), width, sink)
                                if (kind == StrokeShapes.Kind.FILL) drawPath(sink.path, color)
                                else drawPath(sink.path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                    }

                    if (lassoPathPoints.isNotEmpty() && currentTool == CanvasTool.LASSO) {
                        val path = Path().apply {
                            moveTo(lassoPathPoints[0].x, lassoPathPoints[0].y)
                            for (i in 1 until lassoPathPoints.size) lineTo(lassoPathPoints[i].x, lassoPathPoints[i].y)
                            close()
                        }
                        drawPath(path, Color.Blue.copy(alpha = 0.6f), style = Stroke(width = 1f / zoomScale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)))
                        drawPath(path, Color.Blue.copy(alpha = 0.05f))
                    }
                }
            }
        }

        textPosition?.let { pos ->
            val screenPos = pos * zoomScale + panOffset
            BasicTextField(
                value = textValue,
                onValueChange = { textValue = it },
                textStyle = TextStyle(
                    color = if (isDarkTheme) invertColor(currentColor) else currentColor,
                    fontSize = with(density) { (currentFontSize * zoomScale).toSp() },
                    fontFamily = when (currentFontFamily) {
                        "Serif" -> FontFamily.Serif
                        "SansSerif" -> FontFamily.SansSerif
                        "Monospace" -> FontFamily.Monospace
                        else -> FontFamily.Default
                    }
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .offset { IntOffset(screenPos.x.roundToInt(), screenPos.y.roundToInt()) }
                    .focusRequester(focusRequester)
            )
        }
    }
}

private fun heldButtonAction(buttons: PointerButtons, config: StylusConfig): StylusButtonAction = when {
    buttons.isPrimaryPressed && config.primaryButton != StylusButtonAction.UNDO -> config.primaryButton
    buttons.isSecondaryPressed && config.secondaryButton != StylusButtonAction.UNDO -> config.secondaryButton
    else -> StylusButtonAction.NONE
}

/** Single-pointer pan that upgrades to pinch zoom when a second pointer arrives. */
private suspend fun AwaitPointerEventScope.panUntilRelease(
    pointerId: androidx.compose.ui.input.pointer.PointerId,
    pan: () -> Offset,
    zoom: () -> Float,
    update: (Offset, Float) -> Unit
) {
    var last: Offset? = null
    do {
        val event = awaitPointerEvent()
        if (event.changes.count { it.pressed } >= 2) {
            zoomUntilRelease(pan, zoom, update)
            return
        }
        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
        if (change.pressed) {
            val previous = last ?: change.previousPosition
            update(pan() + (change.position - previous), zoom())
            last = change.position
            change.consume()
        }
    } while (event.changes.any { it.pressed })
}

/** Two-pointer pan and pinch zoom until every pointer lifts. */
private suspend fun AwaitPointerEventScope.zoomUntilRelease(
    pan: () -> Offset,
    zoom: () -> Float,
    update: (Offset, Float) -> Unit
) {
    var prevFocal: Offset? = null
    var prevSpan = 0f
    do {
        val event = awaitPointerEvent()
        val pointers = event.changes.filter { it.pressed }
        if (pointers.size >= 2) {
            val focal = (pointers[0].position + pointers[1].position) / 2f
            val span = (pointers[0].position - pointers[1].position).getDistance()
            val lastFocal = prevFocal
            if (lastFocal != null && prevSpan > 0f) {
                val oldZoom = zoom()
                val newZoom = (oldZoom * span / prevSpan).coerceIn(0.1f, 10f)
                val factor = newZoom / oldZoom
                update(focal - (lastFocal - pan()) * factor, newZoom)
            }
            prevFocal = focal
            prevSpan = span
            pointers.forEach { it.consume() }
        }
    } while (event.changes.any { it.pressed })
}

private fun invertColor(color: Color): Color {
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return when {
        luminance < 0.1f -> Color.White.copy(alpha = color.alpha)
        luminance > 0.9f -> Color.Black.copy(alpha = color.alpha)
        else -> color
    }
}

/** Paper pattern over the visible part of the infinite canvas, in content space. */
private fun DrawScope.drawTemplate(template: PageTemplate, pan: Offset, zoom: Float) {
    if (template == PageTemplate.PLAIN) return
    val spacing = when (template) {
        PageTemplate.RULED -> 64f
        PageTemplate.DOTTED -> 40f
        else -> 50f
    }
    val left = -pan.x / zoom
    val top = -pan.y / zoom
    val right = (size.width - pan.x) / zoom
    val bottom = (size.height - pan.y) / zoom
    val color = Color.Gray.copy(alpha = if (template == PageTemplate.DOTTED) 0.35f else 0.15f)
    val lineWidth = 1f / zoom
    var x = floor(left / spacing) * spacing
    var y = floor(top / spacing) * spacing
    when (template) {
        PageTemplate.GRID -> {
            while (x <= right) { drawLine(color, Offset(x, top), Offset(x, bottom), lineWidth); x += spacing }
            while (y <= bottom) { drawLine(color, Offset(left, y), Offset(right, y), lineWidth); y += spacing }
        }
        PageTemplate.RULED -> {
            while (y <= bottom) { drawLine(color, Offset(left, y), Offset(right, y), lineWidth); y += spacing }
        }
        PageTemplate.DOTTED -> {
            val radius = 1.5f / zoom.coerceAtLeast(0.5f)
            while (y <= bottom) {
                var dx = x
                while (dx <= right) { drawCircle(color, radius, Offset(dx, y)); dx += spacing }
                y += spacing
            }
        }
        PageTemplate.PLAIN -> Unit
    }
}

private fun DrawScope.drawStroke(
    stroke: StrokeData,
    shape: StrokeShape,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    textPaint: android.graphics.Paint
) {
    val baseColor = runCatching { Color(AndroidColor.parseColor(stroke.color)) }.getOrDefault(Color.Black)
    val toolColor = if (isDarkTheme) invertColor(baseColor) else baseColor
    val color = if (isSelected) Color.Blue else toolColor

    if (stroke.tool == "text" && stroke.text != null && stroke.points.isNotEmpty()) {
        textPaint.color = color.toArgb()
        textPaint.textSize = stroke.fontSize
        textPaint.typeface = PdfRenderer.typefaceFor(stroke.fontFamily)
        drawContext.canvas.nativeCanvas.drawText(
            stroke.text,
            stroke.points[0].x,
            stroke.points[0].y + stroke.fontSize * 0.8f,
            textPaint
        )
        return
    }
    if (stroke.points.isEmpty()) return

    if (isSelected) {
        drawPath(shape.path, Color.Blue.copy(alpha = 0.2f), style = Stroke(width = stroke.strokeWidth + 10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    if (shape.kind == StrokeShapes.Kind.FILL) {
        drawPath(shape.path, color)
    } else {
        val width = if (isSelected) stroke.strokeWidth + 2f else stroke.strokeWidth
        drawPath(shape.path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawPolyline(points: List<Offset>, color: Color, style: Stroke) {
    if (points.isEmpty()) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    drawPath(path, color, style = style)
}
