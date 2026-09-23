package uk.kayalab.mynotes.ui.canvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class ShapeKind(val label: String) { LINE("Line"), ARROW("Arrow"), RECTANGLE("Rectangle"), ELLIPSE("Ellipse") }

/**
 * Shapes are flattened to polylines at creation, so erase, lasso, PDF and thumbnails treat
 * them like any other stroke with tool "shape".
 */
object ShapeGeometry {
    private const val ELLIPSE_SEGMENTS = 48
    private const val ARROW_HEAD_ANGLE = PI / 7

    fun points(kind: ShapeKind, start: Offset, end: Offset, strokeWidth: Float = 5f): List<Offset> = when (kind) {
        ShapeKind.LINE -> listOf(start, end)
        ShapeKind.ARROW -> arrow(start, end, headLength = (strokeWidth * 4f).coerceIn(18f, 60f))
        ShapeKind.RECTANGLE -> listOf(start, Offset(end.x, start.y), end, Offset(start.x, end.y), start)
        ShapeKind.ELLIPSE -> ellipse(start, end)
    }

    private fun arrow(start: Offset, end: Offset, headLength: Float): List<Offset> {
        val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
        val left = Offset(
            end.x - (headLength * cos(angle - ARROW_HEAD_ANGLE)).toFloat(),
            end.y - (headLength * sin(angle - ARROW_HEAD_ANGLE)).toFloat()
        )
        val right = Offset(
            end.x - (headLength * cos(angle + ARROW_HEAD_ANGLE)).toFloat(),
            end.y - (headLength * sin(angle + ARROW_HEAD_ANGLE)).toFloat()
        )
        return listOf(start, end, left, end, right)
    }

    private fun ellipse(start: Offset, end: Offset): List<Offset> {
        val centre = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
        val rx = kotlin.math.abs(end.x - start.x) / 2f
        val ry = kotlin.math.abs(end.y - start.y) / 2f
        val out = ArrayList<Offset>(ELLIPSE_SEGMENTS + 1)
        for (i in 0..ELLIPSE_SEGMENTS) {
            val t = 2.0 * PI * i / ELLIPSE_SEGMENTS
            out.add(Offset(centre.x + (rx * cos(t)).toFloat(), centre.y + (ry * sin(t)).toFloat()))
        }
        return out
    }
}
