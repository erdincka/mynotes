package uk.kayalab.mynotes.ui.canvas

import android.graphics.Color as AndroidColor
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.kayalab.mynotes.ui.NoteViewModel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

@Composable
fun CanvasScreen(
    noteId: Long,
    viewModel: NoteViewModel,
    currentTool: CanvasTool,
    currentColor: Color,
    currentStrokeWidth: Float,
    currentFontSize: Float,
    currentFontFamily: String,
    onToolSelected: (CanvasTool) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val strokes by viewModel.strokes.collectAsState()
    val selectedIds by viewModel.selectedStrokeIds.collectAsState()

    LaunchedEffect(noteId) {
        viewModel.loadNote(noteId)
    }

    CanvasView(
        strokes = strokes,
        selectedIds = selectedIds,
        onStrokeAdded = { viewModel.addStroke(it) },
        onEraserStart = { viewModel.startErasing() },
        onEraserAction = { point, radius -> viewModel.eraseAt(point, radius) },
        onLassoComplete = { viewModel.selectStrokesInPath(it) },
        onSelectionMove = { viewModel.moveSelectedStrokes(it) },
        onMoveCommit = { viewModel.commitMove() },
        onClearSelection = { viewModel.clearSelection() },
        onToolSelected = onToolSelected,
        currentTool = currentTool,
        currentColor = currentColor,
        currentStrokeWidth = currentStrokeWidth,
        currentFontSize = currentFontSize,
        currentFontFamily = currentFontFamily,
        modifier = modifier
    )
}

enum class CanvasTool { PEN, BRUSH, ERASER, HIGHLIGHTER, LASSO, TEXT }

object OffsetSerializer : KSerializer<Offset> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Offset", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Offset) {
        encoder.encodeString("${value.x},${value.y}")
    }
    override fun deserialize(decoder: Decoder): Offset {
        val (x, y) = decoder.decodeString().split(",").map { it.toFloat() }
        return Offset(x, y)
    }
}

// Monotonically increasing ID — prevents collisions from currentTimeMillis() within same ms
private val _strokeIdCounter = AtomicLong(System.currentTimeMillis())
internal fun nextStrokeId(): Long = _strokeIdCounter.incrementAndGet()

@Serializable
data class StrokeData(
     val id: Long = nextStrokeId(),
     val points: List<@Serializable(with = OffsetSerializer::class) Offset>,
     val pressures: List<Float> = emptyList(),
     val color: String = "#000000",
     val strokeWidth: Float = 5f,
     val tool: String = "pen",
     val text: String? = null,
     val fontSize: Float = 32f,
     val fontFamily: String = "Default"
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CanvasView(
     strokes: List<StrokeData>,
     selectedIds: Set<Long>,
     onStrokeAdded: (StrokeData) -> Unit,
     onEraserStart: () -> Unit,
     onEraserAction: (Offset, Float) -> Unit,
     onLassoComplete: (List<Offset>) -> Unit,
     onSelectionMove: (Offset) -> Unit,
     onMoveCommit: () -> Unit,
     onClearSelection: () -> Unit,
     onToolSelected: (CanvasTool) -> Unit = {},
     modifier: Modifier = Modifier,
     currentTool: CanvasTool = CanvasTool.PEN,
     currentColor: Color = Color.Black,
     currentStrokeWidth: Float = 5f,
     currentFontSize: Float = 32f,
     currentFontFamily: String = "Default",
) {
     var panOffset by remember { mutableStateOf(Offset.Zero) }
     var zoomScale by remember { mutableFloatStateOf(1f) }

     val currentStrokePoints = remember { mutableStateListOf<Offset>() }
     val currentPressures = remember { mutableStateListOf<Float>() }
     var isDrawing by remember { mutableStateOf(false) }

     var lassoPathPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
     var selectionOffset by remember { mutableStateOf(Offset.Zero) }
     val onToolSelectedState = rememberUpdatedState(onToolSelected)
     val isDarkTheme = isSystemInDarkTheme()
     val view = LocalView.current

     // Stylus barrel buttons arrive as buttonState transitions in generic motion events
     // (not as ACTION_BUTTON_PRESS on this device family). Both strategies are kept so
     // the code works across OEMs.
     DisposableEffect(view) {
         var prevButtonState = 0
         val listener = View.OnGenericMotionListener { _, event ->
             val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                 event.source and InputDevice.SOURCE_STYLUS != 0
             if (!isStylus) return@OnGenericMotionListener false

             val currButtonState = event.buttonState
             if (currButtonState != prevButtonState) {
                 val newlyPressed = currButtonState and prevButtonState.inv()
                 if (newlyPressed and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) {
                     onToolSelectedState.value(CanvasTool.PEN)
                 }
                 if (newlyPressed and MotionEvent.BUTTON_STYLUS_SECONDARY != 0) {
                     onToolSelectedState.value(CanvasTool.ERASER)
                 }
                 prevButtonState = currButtonState
             }

             when (event.actionMasked) {
                 MotionEvent.ACTION_BUTTON_PRESS -> when (event.actionButton) {
                     MotionEvent.BUTTON_STYLUS_PRIMARY -> { onToolSelectedState.value(CanvasTool.PEN); true }
                     MotionEvent.BUTTON_STYLUS_SECONDARY -> { onToolSelectedState.value(CanvasTool.ERASER); true }
                     else -> false
                 }
                 else -> false
             }
         }
         view.setOnGenericMotionListener(listener)
         onDispose { view.setOnGenericMotionListener(null) }
     }

     // Text tool state
     var textPosition by remember { mutableStateOf<Offset?>(null) }
     var textValue by remember { mutableStateOf("") }
     val focusRequester = remember { FocusRequester() }
     val focusManager = LocalFocusManager.current
     val density = LocalDensity.current

     // Use updated state to avoid restarting pointerInput when these change
     val currentStrokesState = rememberUpdatedState(strokes)
     val currentSelectedIdsState = rememberUpdatedState(selectedIds)
     val currentToolState = rememberUpdatedState(currentTool)
     val currentColorState = rememberUpdatedState(currentColor)
     val currentStrokeWidthState = rememberUpdatedState(currentStrokeWidth)
     val currentFontSizeState = rememberUpdatedState(currentFontSize)
     val currentFontFamilyState = rememberUpdatedState(currentFontFamily)
     val currentPanOffsetState = rememberUpdatedState(panOffset)
     val currentZoomScaleState = rememberUpdatedState(zoomScale)

     val commitText = {
         if (textValue.isNotEmpty() && textPosition != null) {
             val colorHex = "#%08X".format(currentColor.toArgb())
             onStrokeAdded(StrokeData(
                 points = listOf(textPosition!!),
                 color = colorHex,
                 strokeWidth = currentStrokeWidth,
                 tool = "text",
                 text = textValue,
                 fontSize = currentFontSize,
                 fontFamily = currentFontFamily
             ))
         }
         textValue = ""
         textPosition = null
         focusManager.clearFocus()
     }

     // Commit text when tool changes
     LaunchedEffect(currentTool) {
         if (currentTool != CanvasTool.TEXT) {
             commitText()
             lassoPathPoints = emptyList()
             onClearSelection()
         }
     }

     Box(modifier = modifier
         .fillMaxSize()
         .pointerInteropFilter { event ->
             when (event.actionMasked) {
                 MotionEvent.ACTION_BUTTON_PRESS -> when (event.actionButton) {
                     MotionEvent.BUTTON_STYLUS_PRIMARY -> { onToolSelectedState.value(CanvasTool.PEN); true }
                     MotionEvent.BUTTON_STYLUS_SECONDARY -> { onToolSelectedState.value(CanvasTool.ERASER); true }
                     else -> false
                 }
                 else -> false
             }
         }
     ) {
         Canvas(
             modifier = Modifier
                 .fillMaxSize()
                 .pointerInput(Unit) {
                     awaitEachGesture {
                         val firstDown = awaitFirstDown(requireUnconsumed = false)
                         firstDown.consume()

                         val toolType = firstDown.type
                         val effectiveTool = if (toolType == PointerType.Eraser) CanvasTool.ERASER else currentToolState.value
                         val startPos = (firstDown.position - currentPanOffsetState.value) / currentZoomScaleState.value

                         if (effectiveTool == CanvasTool.TEXT) {
                             if (textPosition != null) commitText()
                             textPosition = startPos
                             textValue = ""
                             return@awaitEachGesture
                         }

                         if (effectiveTool == CanvasTool.LASSO && currentSelectedIdsState.value.isNotEmpty()) {
                             val touchedSelected = currentStrokesState.value.any { it.id in currentSelectedIdsState.value && isPointNearStroke(startPos, it) }
                             if (touchedSelected) {
                                 var lastPos = startPos
                                 selectionOffset = Offset.Zero
                                 do {
                                     val event = awaitPointerEvent()
                                     if (event.changes.size > 1) break
                                     val change = event.changes.firstOrNull { it.id == firstDown.id }
                                     if (change != null && change.pressed) {
                                         val currentPos = (change.position - currentPanOffsetState.value) / currentZoomScaleState.value
                                         val delta = currentPos - lastPos
                                         selectionOffset += delta
                                         lastPos = currentPos
                                         change.consume()
                                     }
                                 } while (event.changes.any { it.pressed && it.id == firstDown.id })
                                 onSelectionMove(selectionOffset)
                                 onMoveCommit()
                                 selectionOffset = Offset.Zero
                                 lassoPathPoints = emptyList()
                                 return@awaitEachGesture
                             }
                         }

                         if (effectiveTool == CanvasTool.LASSO) {
                             lassoPathPoints = emptyList()
                             onClearSelection()
                         }

                         isDrawing = true
                         currentStrokePoints.clear(); currentStrokePoints.add(startPos)
                         currentPressures.clear(); currentPressures.add(firstDown.pressure)

                         if (effectiveTool == CanvasTool.ERASER) {
                             onEraserStart()
                             onEraserAction(startPos, currentStrokeWidthState.value / currentZoomScaleState.value)
                         }

                         do {
                             val event = awaitPointerEvent()

                             // Two-finger touch: cancel current stroke and switch to pan/zoom
                             if (event.changes.count { it.pressed } >= 2) {
                                 isDrawing = false
                                 currentStrokePoints.clear()
                                 currentPressures.clear()

                                 val two = event.changes.filter { it.pressed }
                                 var prevFocal = (two[0].position + two[1].position) / 2f
                                 var prevSpan = (two[0].position - two[1].position).getDistance()
                                 event.changes.forEach { it.consume() }

                                 do {
                                     val mEvent = awaitPointerEvent()
                                     val fingers = mEvent.changes.filter { it.pressed }
                                     if (fingers.size >= 2) {
                                         val focal = (fingers[0].position + fingers[1].position) / 2f
                                         val span = (fingers[0].position - fingers[1].position).getDistance()
                                         val scaleFactor = if (prevSpan > 0f) span / prevSpan else 1f
                                         val oldZoom = zoomScale
                                         val newZoom = (oldZoom * scaleFactor).coerceIn(0.1f, 10f)
                                         val actualFactor = newZoom / oldZoom
                                         panOffset = focal - (prevFocal - panOffset) * actualFactor
                                         zoomScale = newZoom
                                         prevFocal = focal
                                         prevSpan = span
                                         fingers.forEach { it.consume() }
                                     }
                                 } while (mEvent.changes.any { it.pressed })

                                 return@awaitEachGesture
                             }

                             val change = event.changes.firstOrNull { it.id == firstDown.id }
                             if (change != null && change.pressed) {
                                 val pos = (change.position - currentPanOffsetState.value) / currentZoomScaleState.value
                                 currentStrokePoints.add(pos)
                                 currentPressures.add(change.pressure)
                                 if (effectiveTool == CanvasTool.ERASER) {
                                     onEraserAction(pos, currentStrokeWidthState.value / currentZoomScaleState.value)
                                 }
                                 change.consume()
                             }
                         } while (event.changes.any { it.pressed && it.id == firstDown.id })

                         if (isDrawing && currentStrokePoints.size > 1) {
                             if (effectiveTool == CanvasTool.LASSO) {
                                 val closeThreshold = 80f / zoomScale
                                 val isClosed = currentStrokePoints.size > 3 &&
                                     currentStrokePoints.takeLast(minOf(8, currentStrokePoints.size))
                                         .any { (it - currentStrokePoints.first()).getDistance() < closeThreshold }
                                 if (isClosed) {
                                     val snapshot = currentStrokePoints.toList()
                                     lassoPathPoints = snapshot
                                     onLassoComplete(snapshot)
                                 } else {
                                     lassoPathPoints = emptyList()
                                     onClearSelection()
                                 }
                             } else if (effectiveTool != CanvasTool.ERASER) {
                                 val colorHex = when (effectiveTool) {
                                     CanvasTool.HIGHLIGHTER -> "#40%06X".format(currentColorState.value.toArgb() and 0xFFFFFF)
                                     else -> "#%08X".format(currentColorState.value.toArgb())
                                 }
                                 onStrokeAdded(StrokeData(
                                     points = currentStrokePoints.toList(),
                                     pressures = currentPressures.toList(),
                                     color = colorHex,
                                     strokeWidth = currentStrokeWidthState.value,
                                     tool = effectiveTool.name.lowercase(),
                                     fontSize = currentFontSizeState.value,
                                     fontFamily = currentFontFamilyState.value
                                 ))
                             }
                         }

                         isDrawing = false
                         currentStrokePoints.clear()
                         currentPressures.clear()
                     }
                 }
         ) {
             translate(left = panOffset.x, top = panOffset.y) {
                 scale(zoomScale, pivot = Offset.Zero) {
                     drawGrid()
                     strokes.forEach { 
                         val isSelected = it.id in selectedIds
                         val offset = if (isSelected) selectionOffset else Offset.Zero
                         drawStrokePath(it, isSelected, isDarkTheme, density, offset) 
                     }
                     
                     if (isDrawing && currentStrokePoints.isNotEmpty()) {
                         val drawColor = when (currentTool) {
                             CanvasTool.HIGHLIGHTER -> currentColor.copy(alpha = 0.25f)
                             CanvasTool.ERASER -> Color.LightGray.copy(alpha = 0.5f)
                             else -> currentColor
                         }
                         val finalDrawColor = if (isDarkTheme) invertColor(drawColor) else drawColor
                         val style = if (currentTool == CanvasTool.LASSO) {
                             Stroke(width = 1f / zoomScale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                         } else {
                             null
                         }
                         drawCurrentStroke(currentStrokePoints, finalDrawColor, currentStrokeWidth, style)
                     }
                     
                     if (lassoPathPoints.isNotEmpty() && currentTool == CanvasTool.LASSO) {
                         val path = Path().apply {
                             moveTo(lassoPathPoints[0].x, lassoPathPoints[0].y)
                             for (i in 1 until lassoPathPoints.size) lineTo(lassoPathPoints[i].x, lassoPathPoints[i].y)
                             close()
                         }
                         drawPath(path, Color.Blue.copy(alpha = 0.15f), style = Stroke(width = 1f / zoomScale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)))
                         drawPath(path, Color.Blue.copy(alpha = 0.05f))
                     }
                 }
             }
         }

         // Overlay for Text Input
         textPosition?.let { pos ->
             val screenPos = pos * zoomScale + panOffset
             BasicTextField(
                 value = textValue,
                 onValueChange = { textValue = it },
                 textStyle = TextStyle(
                     color = if (isDarkTheme) invertColor(currentColor) else currentColor, 
                     fontSize = (currentFontSize * zoomScale).sp,
                     fontFamily = when(currentFontFamily) {
                         "Serif" -> FontFamily.Serif
                         "SansSerif" -> FontFamily.SansSerif
                         "Monospace" -> FontFamily.Monospace
                         else -> FontFamily.Default
                     }
                 ),
                 keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                 modifier = Modifier
                     .offset { IntOffset(screenPos.x.roundToInt(), screenPos.y.roundToInt()) }
                     .focusRequester(focusRequester),
                 onTextLayout = {
                     focusRequester.requestFocus()
                 }
             )
         }

     }
}

private fun isPointNearStroke(point: Offset, stroke: StrokeData): Boolean {
    val threshold = stroke.strokeWidth + 20f
    return stroke.points.any { (it - point).getDistance() < threshold }
}

private fun invertColor(color: Color): Color {
    val r = color.red
    val g = color.green
    val b = color.blue
    val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
    return if (luminance < 0.1f) {
        Color.White.copy(alpha = color.alpha)
    } else if (luminance > 0.9f) {
        Color.Black.copy(alpha = color.alpha)
    } else {
        color
    }
}

private fun DrawScope.drawGrid() {
     val gridSize = 50f
     var x = 0f
     while (x <= size.width) {
         drawLine(Color.LightGray.copy(alpha = 0.1f), Offset(x, 0f), Offset(x, size.height), 1f)
         x += gridSize
     }
     var y = 0f
     while (y <= size.height) {
         drawLine(Color.LightGray.copy(alpha = 0.1f), Offset(0f, y), Offset(size.width, y), 1f)
         y += gridSize
     }
}

private fun DrawScope.drawStrokePath(
    stroke: StrokeData,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    density: androidx.compose.ui.unit.Density,
    previewOffset: Offset = Offset.Zero
) {
    val baseColor = try { Color(AndroidColor.parseColor(stroke.color)) } catch (_: Exception) { Color.Black }
    val toolColor = if (isDarkTheme) invertColor(baseColor) else baseColor
    val color = if (isSelected) Color.Blue else toolColor

    if (stroke.tool == "text" && stroke.text != null && stroke.points.isNotEmpty()) {
        val textSizePx = with(density) { stroke.fontSize.sp.toPx() }
        drawContext.canvas.nativeCanvas.drawText(
            stroke.text,
            stroke.points[0].x + previewOffset.x,
            stroke.points[0].y + (textSizePx * 0.8f) + previewOffset.y,
            android.graphics.Paint().apply {
                this.color = color.toArgb()
                this.textSize = textSizePx
                this.isAntiAlias = true
                this.typeface = when (stroke.fontFamily) {
                    "Serif" -> android.graphics.Typeface.SERIF
                    "SansSerif" -> android.graphics.Typeface.SANS_SERIF
                    "Monospace" -> android.graphics.Typeface.MONOSPACE
                    else -> android.graphics.Typeface.DEFAULT
                }
            }
        )
        return
    }

    if (stroke.points.isEmpty()) return

    val useSmoothCurve = stroke.tool in setOf("pen", "brush", "highlighter") && stroke.points.size >= 3
    val path = if (useSmoothCurve) {
        catmullRomPath(stroke.points, previewOffset)
    } else {
        Path().apply {
            moveTo(stroke.points[0].x + previewOffset.x, stroke.points[0].y + previewOffset.y)
            for (i in 1 until stroke.points.size) {
                lineTo(stroke.points[i].x + previewOffset.x, stroke.points[i].y + previewOffset.y)
            }
        }
    }

    val style = if (stroke.tool == "lasso") {
        Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
    } else {
        val width = if (isSelected) stroke.strokeWidth + 2f else stroke.strokeWidth
        Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }

    if (isSelected) {
        drawPath(
            path,
            Color.Blue.copy(alpha = 0.2f),
            style = Stroke(width = stroke.strokeWidth + 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    drawPath(path, color, style = style)
}

/**
 * Builds a smooth Catmull-Rom spline path through [points], converted to cubic Bézier segments.
 * Conversion: b1 = P1 + (P2−P0)/6,  b2 = P2 − (P3−P1)/6
 */
private fun catmullRomPath(points: List<Offset>, offset: Offset): Path {
    val path = Path()
    path.moveTo(points[0].x + offset.x, points[0].y + offset.y)
    if (points.size < 3) {
        for (i in 1 until points.size) {
            path.lineTo(points[i].x + offset.x, points[i].y + offset.y)
        }
        return path
    }
    for (i in 0 until points.size - 1) {
        val p0 = points[if (i > 0) i - 1 else 0]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[if (i + 2 < points.size) i + 2 else points.size - 1]

        val b1x = p1.x + (p2.x - p0.x) / 6f + offset.x
        val b1y = p1.y + (p2.y - p0.y) / 6f + offset.y
        val b2x = p2.x - (p3.x - p1.x) / 6f + offset.x
        val b2y = p2.y - (p3.y - p1.y) / 6f + offset.y

        path.cubicTo(b1x, b1y, b2x, b2y, p2.x + offset.x, p2.y + offset.y)
    }
    return path
}

private fun DrawScope.drawCurrentStroke(
    points: List<Offset>,
    color: Color,
    baseWidth: Float,
    customStyle: Stroke? = null
) {
    if (points.isEmpty()) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    val style = customStyle ?: Stroke(width = baseWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(path, color, style = style)
}
