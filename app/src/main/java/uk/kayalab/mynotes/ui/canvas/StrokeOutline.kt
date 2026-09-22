package uk.kayalab.mynotes.ui.canvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns a centreline with per-point pressure into a filled outline polygon, so pen and brush
 * strokes vary in width. Pure so the screen, the PDF and the unit tests share one shape.
 */
object StrokeOutline {

    data class Joint(val centre: Offset, val radius: Float)

    /** [polygon] is closed implicitly; [joints] are round caps and sharp-turn fillers. */
    data class Outline(val polygon: List<Offset>, val joints: List<Joint>)

    private const val PRESSURE_VARIATION_THRESHOLD = 0.05f
    private const val SHARP_TURN_COS = 0.85f
    private const val SUBDIVISIONS = 2

    /** Pressure only changes width for pen and brush; other tools stay uniform. */
    fun usesPressure(stroke: StrokeData): Boolean =
        (stroke.tool == "pen" || stroke.tool == "brush") && hasVariation(stroke.pressures)

    fun hasVariation(pressures: List<Float>): Boolean {
        if (pressures.size < 2) return false
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (p in pressures) {
            if (p < min) min = p
            if (p > max) max = p
        }
        return max - min > PRESSURE_VARIATION_THRESHOLD
    }

    /** Full stroke width for one sample. Pen is subtle, brush is expressive. */
    fun widthFor(tool: String, baseWidth: Float, pressure: Float): Float {
        val p = pressure.coerceIn(0f, 1f)
        return when (tool) {
            "brush" -> baseWidth * (0.25f + 1.0f * p)
            "pen" -> baseWidth * (0.65f + 0.45f * p)
            else -> baseWidth
        }
    }

    /** Exponential smoothing so sensor jitter does not become a wobbly line. */
    fun smoothPressure(previous: Float?, raw: Float, alpha: Float = 0.4f): Float =
        if (previous == null) raw else previous + alpha * (raw - previous)

    /** Catmull-Rom subdivision of the centreline with linearly interpolated pressures. */
    fun resample(points: List<Offset>, pressures: List<Float>): Pair<List<Offset>, List<Float>> {
        if (points.size < 3) return points to pressures.padTo(points.size)
        val padded = pressures.padTo(points.size)
        val outPoints = ArrayList<Offset>(points.size * SUBDIVISIONS)
        val outPressures = ArrayList<Float>(points.size * SUBDIVISIONS)
        outPoints.add(points[0])
        outPressures.add(padded[0])
        for (i in 0 until points.size - 1) {
            val p0 = points[if (i > 0) i - 1 else 0]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[if (i + 2 < points.size) i + 2 else points.size - 1]
            for (step in 1..SUBDIVISIONS) {
                val t = step.toFloat() / SUBDIVISIONS
                outPoints.add(catmullRom(p0, p1, p2, p3, t))
                outPressures.add(padded[i] + (padded[i + 1] - padded[i]) * t)
            }
        }
        return outPoints to outPressures
    }

    /** Builds the outline for [points] with a full [widths] value per point. */
    fun build(points: List<Offset>, widths: List<Float>): Outline {
        require(points.size == widths.size) { "points and widths differ in length" }
        if (points.isEmpty()) return Outline(emptyList(), emptyList())
        if (points.size == 1) return Outline(emptyList(), listOf(Joint(points[0], widths[0] / 2f)))

        val left = ArrayList<Offset>(points.size)
        val right = ArrayList<Offset>(points.size)
        val joints = ArrayList<Joint>()
        joints.add(Joint(points.first(), widths.first() / 2f))
        joints.add(Joint(points.last(), widths.last() / 2f))

        var previousTangent: Offset? = null
        for (i in points.indices) {
            val before = points[if (i > 0) i - 1 else 0]
            val after = points[if (i < points.size - 1) i + 1 else points.size - 1]
            val tangent = normalise(after - before) ?: previousTangent ?: Offset(1f, 0f)
            val prev = previousTangent
            if (prev != null && dot(prev, tangent) < SHARP_TURN_COS) {
                joints.add(Joint(points[i], widths[i] / 2f))
            }
            previousTangent = tangent
            val normal = Offset(-tangent.y, tangent.x) * (widths[i] / 2f)
            left.add(points[i] + normal)
            right.add(points[i] - normal)
        }
        val polygon = ArrayList<Offset>(left.size * 2)
        polygon.addAll(left)
        for (i in right.indices.reversed()) polygon.add(right[i])
        return Outline(polygon, joints)
    }

    /** Points of a circle wound the same way as [sameWindingAs], so non-zero filling unions them. */
    fun circlePoints(joint: Joint, sameWindingAs: List<Offset>, segments: Int = 12): List<Offset> {
        val out = ArrayList<Offset>(segments)
        for (k in 0 until segments) {
            val angle = 2.0 * PI * k / segments
            out.add(Offset(joint.centre.x + joint.radius * cos(angle).toFloat(), joint.centre.y + joint.radius * sin(angle).toFloat()))
        }
        val polygonArea = signedArea(sameWindingAs)
        return if (polygonArea != 0f && polygonArea * signedArea(out) < 0f) out.asReversed() else out
    }

    fun signedArea(polygon: List<Offset>): Float {
        if (polygon.size < 3) return 1f
        var area = 0f
        var j = polygon.size - 1
        for (i in polygon.indices) {
            area += (polygon[j].x + polygon[i].x) * (polygon[j].y - polygon[i].y)
            j = i
        }
        return area / 2f
    }

    private fun catmullRom(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
        val t2 = t * t
        val t3 = t2 * t
        val x = 0.5f * ((2 * p1.x) + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3)
        val y = 0.5f * ((2 * p1.y) + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3)
        return Offset(x, y)
    }

    private fun normalise(v: Offset): Offset? {
        val length = sqrt(v.x * v.x + v.y * v.y)
        return if (length < 1e-4f) null else Offset(v.x / length, v.y / length)
    }

    private fun dot(a: Offset, b: Offset) = a.x * b.x + a.y * b.y

    private fun List<Float>.padTo(size: Int): List<Float> =
        if (this.size >= size) take(size) else this + List(size - this.size) { lastOrNull() ?: 1f }
}
