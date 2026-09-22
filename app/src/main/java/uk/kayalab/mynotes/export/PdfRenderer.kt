package uk.kayalab.mynotes.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.geometry.Offset
import uk.kayalab.mynotes.ui.canvas.AndroidPathSink
import uk.kayalab.mynotes.ui.canvas.StrokeData
import uk.kayalab.mynotes.ui.canvas.StrokeGeometry
import uk.kayalab.mynotes.ui.canvas.StrokeShapes
import java.io.OutputStream

/** Renders strokes to a multi-page A4 PDF. Every stroke is drawn on every page; the page clips. */
object PdfRenderer {

    fun render(strokes: List<StrokeData>, referenceWidth: Float, output: OutputStream) {
        val document = PdfDocument()
        try {
            val bounds = StrokeGeometry.boundingBox(strokes)
            if (bounds == null) {
                val page = document.startPage(pageInfo(1))
                document.finishPage(page)
            } else {
                val layout = PdfLayout.compute(bounds, referenceWidth)
                for (pageIndex in 0 until layout.pageCount) {
                    val page = document.startPage(pageInfo(pageIndex + 1))
                    strokes.forEach { drawStroke(page.canvas, it, layout, pageIndex) }
                    document.finishPage(page)
                }
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private fun pageInfo(number: Int) =
        PdfDocument.PageInfo.Builder(PdfLayout.PAGE_WIDTH, PdfLayout.PAGE_HEIGHT, number).create()

    private fun drawStroke(canvas: Canvas, stroke: StrokeData, layout: PdfLayout, pageIndex: Int) {
        if (stroke.points.isEmpty()) return
        if (stroke.tool == "text" && stroke.text != null) {
            val paint = Paint().apply {
                color = parseColor(stroke.color)
                textSize = stroke.fontSize * layout.scale
                isAntiAlias = true
                typeface = typefaceFor(stroke.fontFamily)
            }
            canvas.drawText(
                stroke.text,
                layout.pageX(stroke.points[0].x),
                layout.pageY(stroke.points[0].y, pageIndex) + stroke.fontSize * layout.scale * 0.8f,
                paint
            )
            return
        }
        val sink = AndroidPathSink(transform = { Offset(layout.pageX(it.x), layout.pageY(it.y, pageIndex)) })
        val kind = StrokeShapes.emit(stroke, sink)
        val paint = Paint().apply {
            color = parseColor(stroke.color)
            isAntiAlias = true
            if (kind == StrokeShapes.Kind.FILL) {
                style = Paint.Style.FILL
            } else {
                style = Paint.Style.STROKE
                strokeWidth = stroke.strokeWidth * layout.scale
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        }
        canvas.drawPath(sink.path, paint)
    }

    fun typefaceFor(family: String): Typeface = when (family) {
        "Serif" -> Typeface.SERIF
        "SansSerif" -> Typeface.SANS_SERIF
        "Monospace" -> Typeface.MONOSPACE
        else -> Typeface.DEFAULT
    }

    private fun parseColor(value: String): Int =
        runCatching { Color.parseColor(value) }.getOrDefault(Color.BLACK)
}
