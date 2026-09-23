package uk.kayalab.mynotes.recognition

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test
import uk.kayalab.mynotes.ui.canvas.StrokeData

class LineGroupingTest {
    private fun stroke(id: Long, x: Float, top: Float, height: Float = 40f) =
        StrokeData(id = id, points = listOf(Offset(x, top), Offset(x + 30f, top + height)))

    @Test
    fun strokesOnTheSameBaselineFormOneLineOrderedLeftToRight() {
        val lines = LineGrouping.group(listOf(stroke(1, 200f, 100f), stroke(2, 50f, 105f), stroke(3, 120f, 98f)))
        assertEquals(1, lines.size)
        assertEquals(listOf(2L, 3L, 1L), lines[0].map { it.id })
    }

    @Test
    fun aClearlyLowerStrokeStartsANewLine() {
        val lines = LineGrouping.group(listOf(stroke(1, 0f, 100f), stroke(2, 0f, 220f), stroke(3, 40f, 225f)))
        assertEquals(2, lines.size)
        assertEquals(listOf(1L), lines[0].map { it.id })
        assertEquals(setOf(2L, 3L), lines[1].map { it.id }.toSet())
    }

    @Test
    fun aDotAboveTheLineStaysWithIt() {
        val dot = StrokeData(id = 9, points = listOf(Offset(60f, 92f), Offset(61f, 94f)))
        val lines = LineGrouping.group(listOf(stroke(1, 0f, 100f), dot, stroke(2, 100f, 102f)))
        assertEquals(1, lines.size)
    }
}
