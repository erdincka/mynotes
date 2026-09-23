package uk.kayalab.mynotes.recognition

import uk.kayalab.mynotes.ui.canvas.StrokeData
import uk.kayalab.mynotes.ui.canvas.StrokeGeometry

/**
 * Splits a page of strokes into handwriting lines. The recogniser is built for a line or a short
 * paragraph, so a whole page in one request comes back empty or as nonsense.
 */
object LineGrouping {
    private class Band(var top: Float, var bottom: Float, val strokes: MutableList<StrokeData>) {
        val height: Float get() = bottom - top
    }

    /**
     * Same-sized strokes join when the newcomer's centre sits inside the band; a much smaller mark
     * (a dot on an i, a dash) joins whenever it lies within half a line height of the band.
     */
    private fun belongs(band: Band, top: Float, bottom: Float): Boolean {
        val height = bottom - top
        val bigger = maxOf(band.height, height)
        val smaller = minOf(band.height, height)
        return if (smaller < 0.4f * bigger) {
            val slack = bigger * 0.5f
            bottom >= band.top - slack && top <= band.bottom + slack
        } else {
            val centre = (top + bottom) / 2f
            val slack = bigger * 0.25f
            centre >= band.top - slack && centre <= band.bottom + slack
        }
    }

    fun group(strokes: List<StrokeData>): List<List<StrokeData>> {
        val withBounds = strokes.mapNotNull { stroke -> StrokeGeometry.boundingBox(listOf(stroke))?.let { stroke to it } }
            .sortedBy { (_, b) -> (b.top + b.bottom) / 2f }
        val bands = ArrayList<Band>()
        for ((stroke, bounds) in withBounds) {
            val band = bands.lastOrNull()
            if (band != null && belongs(band, bounds.top, bounds.bottom)) {
                band.top = minOf(band.top, bounds.top)
                band.bottom = maxOf(band.bottom, bounds.bottom)
                band.strokes.add(stroke)
            } else {
                bands.add(Band(bounds.top, bounds.bottom, mutableListOf(stroke)))
            }
        }
        return bands.map { band -> band.strokes.sortedBy { StrokeGeometry.boundingBox(listOf(it))?.left ?: 0f } }
    }
}
