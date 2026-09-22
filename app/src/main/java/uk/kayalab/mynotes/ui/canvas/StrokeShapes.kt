package uk.kayalab.mynotes.ui.canvas

import androidx.compose.ui.geometry.Offset

/**
 * Emits a stroke's geometry into any path type. A pressure stroke becomes a filled outline;
 * everything else becomes a smoothed centreline to be stroked with the tool width.
 */
object StrokeShapes {

    interface PathSink {
        fun moveTo(p: Offset)
        fun lineTo(p: Offset)
        fun cubicTo(c1: Offset, c2: Offset, end: Offset)
        fun close()
    }

    enum class Kind { FILL, STROKE }

    fun emit(stroke: StrokeData, sink: PathSink): Kind =
        emit(stroke.points, stroke.pressures, stroke.tool, stroke.strokeWidth, sink)

    fun emit(points: List<Offset>, pressures: List<Float>, tool: String, baseWidth: Float, sink: PathSink): Kind {
        if (points.isEmpty()) return Kind.STROKE
        val pressureStroke = (tool == "pen" || tool == "brush") && StrokeOutline.hasVariation(pressures)
        if (!pressureStroke) {
            emitCentreline(points, tool, sink)
            return Kind.STROKE
        }
        val (centre, resampled) = StrokeOutline.resample(points, pressures)
        val widths = resampled.map { StrokeOutline.widthFor(tool, baseWidth, it) }
        val outline = StrokeOutline.build(centre, widths)
        emitPolygon(outline.polygon, sink)
        for (joint in outline.joints) {
            emitPolygon(StrokeOutline.circlePoints(joint, outline.polygon), sink)
        }
        return Kind.FILL
    }

    private fun emitPolygon(polygon: List<Offset>, sink: PathSink) {
        if (polygon.size < 3) return
        sink.moveTo(polygon[0])
        for (i in 1 until polygon.size) sink.lineTo(polygon[i])
        sink.close()
    }

    private fun emitCentreline(points: List<Offset>, tool: String, sink: PathSink) {
        sink.moveTo(points[0])
        val smooth = tool in setOf("pen", "brush", "highlighter") && points.size >= 3
        if (!smooth) {
            for (i in 1 until points.size) sink.lineTo(points[i])
            return
        }
        // Catmull-Rom spline expressed as cubic Béziers.
        for (i in 0 until points.size - 1) {
            val p0 = points[if (i > 0) i - 1 else 0]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[if (i + 2 < points.size) i + 2 else points.size - 1]
            sink.cubicTo(
                Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f),
                Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f),
                p2
            )
        }
    }
}

class ComposePathSink(val path: androidx.compose.ui.graphics.Path = androidx.compose.ui.graphics.Path()) : StrokeShapes.PathSink {
    override fun moveTo(p: Offset) = path.moveTo(p.x, p.y)
    override fun lineTo(p: Offset) = path.lineTo(p.x, p.y)
    override fun cubicTo(c1: Offset, c2: Offset, end: Offset) = path.cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
    override fun close() = path.close()
}

/** Android path with an optional content-to-page transform applied to every point. */
class AndroidPathSink(
    private val transform: (Offset) -> Offset = { it },
    val path: android.graphics.Path = android.graphics.Path()
) : StrokeShapes.PathSink {
    override fun moveTo(p: Offset) = transform(p).let { path.moveTo(it.x, it.y) }
    override fun lineTo(p: Offset) = transform(p).let { path.lineTo(it.x, it.y) }
    override fun cubicTo(c1: Offset, c2: Offset, end: Offset) {
        val a = transform(c1); val b = transform(c2); val e = transform(end)
        path.cubicTo(a.x, a.y, b.x, b.y, e.x, e.y)
    }
    override fun close() = path.close()
}
