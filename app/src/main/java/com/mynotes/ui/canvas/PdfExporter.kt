package com.mynotes.ui.canvas

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import com.mynotes.data.Note
import kotlinx.serialization.json.Json
import java.io.OutputStream
import kotlin.math.ceil

private const val PAGE_WIDTH = 595   // A4 width in PDF points
private const val PAGE_HEIGHT = 842  // A4 height in PDF points
private const val PAGE_MARGIN = 20f  // margin in points

class PdfExporter(private val context: Context) {

    fun exportNote(note: Note, outputStream: OutputStream): Result<Unit> {
        return try {
            val strokes: List<StrokeData> = try {
                Json.decodeFromString(note.content)
            } catch (_: Exception) {
                emptyList()
            }

            val document = PdfDocument()

            if (strokes.isEmpty()) {
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
                document.finishPage(page)
                document.writeTo(outputStream)
                document.close()
                return Result.success(Unit)
            }

            // Compute bounding box in a single pass
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            strokes.forEach { stroke ->
                stroke.points.forEach { pt ->
                    if (pt.x < minX) minX = pt.x
                    if (pt.y < minY) minY = pt.y
                    if (pt.x > maxX) maxX = pt.x
                    if (pt.y > maxY) maxY = pt.y
                }
                if (stroke.tool == "text") {
                    val textBottom = (stroke.points.firstOrNull()?.y ?: 0f) + stroke.fontSize
                    if (textBottom > maxY) maxY = textBottom
                }
            }

            val contentWidth = (maxX - minX).coerceAtLeast(1f)
            val usablePageWidth = PAGE_WIDTH - 2 * PAGE_MARGIN
            val usablePageHeight = PAGE_HEIGHT - 2 * PAGE_MARGIN

            // Uniform scale: fit content width to page; same scale applied to height
            val scale = usablePageWidth / contentWidth

            val scaledContentHeight = (maxY - minY) * scale
            val pageCount = ceil(scaledContentHeight / usablePageHeight).toInt().coerceAtLeast(1)

            for (pageIndex in 0 until pageCount) {
                val contentYStart = minY + pageIndex * usablePageHeight / scale
                val contentYEnd = contentYStart + usablePageHeight / scale

                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                // Render all strokes; the PDF canvas clips anything outside page bounds
                strokes.forEach { stroke ->
                    renderStroke(canvas, stroke,
                        xOffset = -minX,
                        yOffset = -contentYStart,
                        scale = scale)
                }

                document.finishPage(page)
            }

            document.writeTo(outputStream)
            document.close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Maps content coords → PDF page coords using scale + offset, then renders. */
    private fun renderStroke(
        canvas: android.graphics.Canvas,
        stroke: StrokeData,
        xOffset: Float,
        yOffset: Float,
        scale: Float
    ) {
        fun px(x: Float) = (x + xOffset) * scale + PAGE_MARGIN
        fun py(y: Float) = (y + yOffset) * scale + PAGE_MARGIN

        if (stroke.tool == "text" && stroke.text != null && stroke.points.isNotEmpty()) {
            val paint = Paint().apply {
                color = parseColor(stroke.color)
                textSize = stroke.fontSize * scale
                isAntiAlias = true
                typeface = when (stroke.fontFamily) {
                    "Serif" -> android.graphics.Typeface.SERIF
                    "SansSerif" -> android.graphics.Typeface.SANS_SERIF
                    "Monospace" -> android.graphics.Typeface.MONOSPACE
                    else -> android.graphics.Typeface.DEFAULT
                }
            }
            canvas.drawText(
                stroke.text,
                px(stroke.points[0].x),
                py(stroke.points[0].y) + stroke.fontSize * scale * 0.8f,
                paint
            )
        } else if (stroke.points.isNotEmpty()) {
            val paint = Paint().apply {
                color = parseColor(stroke.color)
                strokeWidth = stroke.strokeWidth * scale
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }
            val path = buildPath(stroke.points, xOffset, yOffset, scale)
            canvas.drawPath(path, paint)
        }
    }

    private fun buildPath(
        points: List<androidx.compose.ui.geometry.Offset>,
        xOffset: Float,
        yOffset: Float,
        scale: Float
    ): Path {
        fun px(x: Float) = (x + xOffset) * scale + PAGE_MARGIN
        fun py(y: Float) = (y + yOffset) * scale + PAGE_MARGIN

        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(px(points[0].x), py(points[0].y))
        if (points.size >= 3) {
            for (i in 0 until points.size - 1) {
                val p0 = points[if (i > 0) i - 1 else 0]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[if (i + 2 < points.size) i + 2 else points.size - 1]

                val b1x = px(p1.x + (p2.x - p0.x) / 6f)
                val b1y = py(p1.y + (p2.y - p0.y) / 6f)
                val b2x = px(p2.x - (p3.x - p1.x) / 6f)
                val b2y = py(p2.y - (p3.y - p1.y) / 6f)

                path.cubicTo(b1x, b1y, b2x, b2y, px(p2.x), py(p2.y))
            }
        } else {
            for (i in 1 until points.size) {
                path.lineTo(px(points[i].x), py(points[i].y))
            }
        }
        return path
    }

    private fun parseColor(colorStr: String): Int = try {
        android.graphics.Color.parseColor(colorStr)
    } catch (_: Exception) {
        android.graphics.Color.BLACK
    }
}
