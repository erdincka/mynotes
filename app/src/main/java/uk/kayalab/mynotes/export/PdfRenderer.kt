package uk.kayalab.mynotes.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.geometry.Offset
import uk.kayalab.mynotes.ui.canvas.StrokeData
import uk.kayalab.mynotes.ui.canvas.StrokeGeometry
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
        val paint = Paint().apply {
            color = parseColor(stroke.color)
            strokeWidth = stroke.strokeWidth * layout.scale
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        canvas.drawPath(buildPath(stroke.points, layout, pageIndex), paint)
    }

    private fun buildPath(points: List<Offset>, layout: PdfLayout, pageIndex: Int): Path {
        fun px(x: Float) = layout.pageX(x)
        fun py(y: Float) = layout.pageY(y, pageIndex)
        val path = Path()
        path.moveTo(px(points[0].x), py(points[0].y))
        if (points.size >= 3) {
            for (i in 0 until points.size - 1) {
                val p0 = points[if (i > 0) i - 1 else 0]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[if (i + 2 < points.size) i + 2 else points.size - 1]
                path.cubicTo(
                    px(p1.x + (p2.x - p0.x) / 6f), py(p1.y + (p2.y - p0.y) / 6f),
                    px(p2.x - (p3.x - p1.x) / 6f), py(p2.y - (p3.y - p1.y) / 6f),
                    px(p2.x), py(p2.y)
                )
            }
        } else {
            for (i in 1 until points.size) path.lineTo(px(points[i].x), py(points[i].y))
        }
        return path
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
