package uk.kayalab.mynotes.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import uk.kayalab.mynotes.ui.canvas.AndroidPathSink
import uk.kayalab.mynotes.ui.canvas.StrokeData
import uk.kayalab.mynotes.ui.canvas.StrokeGeometry
import uk.kayalab.mynotes.ui.canvas.StrokeShapes
import java.io.ByteArrayOutputStream

/** Small paper-white preview of a note for the list, as PNG bytes. */
object NoteThumbnailRenderer {
    const val WIDTH = 320
    const val HEIGHT = 240
    private const val PADDING = 12f

    fun render(strokes: List<StrokeData>, images: (String) -> Bitmap? = { null }): ByteArray {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val bounds = StrokeGeometry.boundingBox(strokes)
        if (bounds != null) {
            val scale = minOf(
                (WIDTH - 2 * PADDING) / maxOf(bounds.width, 1f),
                (HEIGHT - 2 * PADDING) / maxOf(bounds.height, 1f),
                1f
            )
            val offsetX = PADDING + ((WIDTH - 2 * PADDING) - bounds.width * scale) / 2f
            val offsetY = PADDING
            val transform: (Offset) -> Offset = { Offset((it.x - bounds.left) * scale + offsetX, (it.y - bounds.top) * scale + offsetY) }
            val paint = Paint().apply { isAntiAlias = true }
            for (stroke in strokes) {
                if (stroke.points.isEmpty()) continue
                if (stroke.isImage) {
                    val bitmap = images(stroke.imageName!!) ?: continue
                    val at = transform(stroke.points[0])
                    canvas.drawBitmap(bitmap, null, RectF(at.x, at.y, at.x + stroke.imageWidth * scale, at.y + stroke.imageHeight * scale), Paint(Paint.FILTER_BITMAP_FLAG))
                    continue
                }
                paint.color = runCatching { Color.parseColor(stroke.color) }.getOrDefault(Color.BLACK)
                if (stroke.tool == "text" && stroke.text != null) {
                    paint.style = Paint.Style.FILL
                    paint.textSize = stroke.fontSize * scale
                    paint.typeface = PdfRenderer.typefaceFor(stroke.fontFamily)
                    val at = transform(stroke.points[0])
                    canvas.drawText(stroke.text, at.x, at.y + stroke.fontSize * scale * 0.8f, paint)
                    continue
                }
                val sink = AndroidPathSink(transform)
                val kind = StrokeShapes.emit(stroke, sink)
                if (kind == StrokeShapes.Kind.FILL) {
                    paint.style = Paint.Style.FILL
                } else {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = maxOf(stroke.strokeWidth * scale, 1f)
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                }
                canvas.drawPath(sink.path, paint)
            }
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        bitmap.recycle()
        return out.toByteArray()
    }
}
