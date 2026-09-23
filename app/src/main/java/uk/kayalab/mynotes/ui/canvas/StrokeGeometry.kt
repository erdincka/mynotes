package uk.kayalab.mynotes.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Pure stroke maths shared by the canvas, the view model and unit tests. */
object StrokeGeometry {

    fun pointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y
            if ((yi > point.y) != (yj > point.y) &&
                point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    fun isPointNearStroke(point: Offset, stroke: StrokeData, slack: Float = 20f): Boolean {
        val threshold = stroke.strokeWidth + slack
        return stroke.points.any { (it - point).getDistance() < threshold }
    }

    fun boundingBox(strokes: List<StrokeData>): Rect? {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var any = false
        for (stroke in strokes) {
            for (pt in stroke.points) {
                any = true
                if (pt.x < minX) minX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.x > maxX) maxX = pt.x
                if (pt.y > maxY) maxY = pt.y
            }
            if (stroke.tool == "text" && stroke.points.isNotEmpty()) {
                val bottom = stroke.points[0].y + stroke.fontSize
                if (bottom > maxY) maxY = bottom
            }
            if (stroke.isImage) {
                val rect = imageRect(stroke)
                if (rect.right > maxX) maxX = rect.right
                if (rect.bottom > maxY) maxY = rect.bottom
            }
        }
        return if (any) Rect(minX, minY, maxX, maxY) else null
    }

    fun imageRect(stroke: StrokeData): Rect =
        Rect(stroke.points[0].x, stroke.points[0].y, stroke.points[0].x + stroke.imageWidth, stroke.points[0].y + stroke.imageHeight)

    /**
     * Removes every point within [radius] of [point], splitting strokes around the gap.
     * Returns null when nothing was touched so callers can skip a state update.
     */
    fun erase(strokes: List<StrokeData>, point: Offset, radius: Float): List<StrokeData>? {
        var changed = false
        val result = ArrayList<StrokeData>(strokes.size)
        for (stroke in strokes) {
            if (stroke.isImage) {
                if (imageRect(stroke).contains(point)) changed = true else result.add(stroke)
                continue
            }
            if (stroke.tool == "text") {
                if (stroke.points.isNotEmpty() && (stroke.points[0] - point).getDistance() <= radius + stroke.fontSize / 2) {
                    changed = true
                } else {
                    result.add(stroke)
                }
                continue
            }
            val threshold = radius + stroke.strokeWidth / 2
            val kept = ArrayList<MutableList<Int>>()
            var segment = ArrayList<Int>()
            var touched = false
            for (i in stroke.points.indices) {
                if ((stroke.points[i] - point).getDistance() > threshold) {
                    segment.add(i)
                } else {
                    touched = true
                    if (segment.isNotEmpty()) {
                        kept.add(segment)
                        segment = ArrayList()
                    }
                }
            }
            if (segment.isNotEmpty()) kept.add(segment)

            if (!touched) {
                result.add(stroke)
            } else {
                changed = true
                for (indices in kept) {
                    if (indices.size < 2) continue
                    result.add(subStroke(stroke, indices, nextStrokeId()))
                }
            }
        }
        return if (changed) result else null
    }

    data class LassoResult(val strokes: List<StrokeData>, val selectedIds: Set<Long>)

    /**
     * Splits each stroke into the parts inside and outside [polygon]; inside parts become the
     * selection. A stroke wholly inside or outside keeps its id so the selection stays stable.
     */
    fun lassoSelect(strokes: List<StrokeData>, polygon: List<Offset>): LassoResult {
        if (polygon.size < 3) return LassoResult(strokes, emptySet())
        val result = ArrayList<StrokeData>(strokes.size)
        val selected = LinkedHashSet<Long>()
        var split = false
        for (stroke in strokes) {
            if (stroke.isImage) {
                result.add(stroke)
                if (pointInPolygon(imageRect(stroke).center, polygon)) selected.add(stroke.id)
                continue
            }
            val inside = ArrayList<MutableList<Int>>()
            val outside = ArrayList<MutableList<Int>>()
            var segment = ArrayList<Int>()
            var wasInside: Boolean? = null
            for (i in stroke.points.indices) {
                val isInside = pointInPolygon(stroke.points[i], polygon)
                if (wasInside == null || isInside == wasInside) {
                    segment.add(i)
                } else {
                    if (wasInside) inside.add(segment) else outside.add(segment)
                    segment = arrayListOf(i)
                    split = true
                }
                wasInside = isInside
            }
            if (wasInside != null) {
                if (wasInside) inside.add(segment) else outside.add(segment)
            }
            val whole = inside.size + outside.size == 1
            for (seg in inside) {
                val piece = if (whole) stroke else subStroke(stroke, seg, nextStrokeId())
                result.add(piece)
                selected.add(piece.id)
            }
            for (seg in outside) {
                result.add(if (whole) stroke else subStroke(stroke, seg, nextStrokeId()))
            }
        }
        return LassoResult(if (split) result else strokes, selected)
    }

    fun move(strokes: List<StrokeData>, ids: Set<Long>, delta: Offset): List<StrokeData> =
        if (ids.isEmpty()) strokes
        else strokes.map { if (it.id in ids) it.copy(points = it.points.map { p -> p + delta }) else it }

    private fun subStroke(stroke: StrokeData, indices: List<Int>, id: Long): StrokeData =
        stroke.copy(
            id = id,
            points = indices.map { stroke.points[it] },
            pressures = indices.map { if (it < stroke.pressures.size) stroke.pressures[it] else 1f }
        )
}
