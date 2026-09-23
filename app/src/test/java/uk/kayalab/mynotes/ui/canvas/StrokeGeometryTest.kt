package uk.kayalab.mynotes.ui.canvas

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeGeometryTest {
    private fun line(id: Long, vararg xs: Float) =
        StrokeData(id = id, points = xs.map { Offset(it, 0f) }, pressures = xs.map { 1f }, strokeWidth = 2f)

    private val square = listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 100f), Offset(0f, 100f))

    @Test
    fun pointInPolygonHandlesInsideAndOutside() {
        assertTrue(StrokeGeometry.pointInPolygon(Offset(50f, 50f), square))
        assertFalse(StrokeGeometry.pointInPolygon(Offset(150f, 50f), square))
    }

    @Test
    fun erasingTheMiddleSplitsAStrokeInTwo() {
        val stroke = line(1, 0f, 10f, 20f, 30f, 40f, 50f)
        val result = StrokeGeometry.erase(listOf(stroke), Offset(25f, 0f), radius = 6f)!!
        assertEquals(2, result.size)
        assertEquals(listOf(0f, 10f), result[0].points.map { it.x })
        assertEquals(listOf(40f, 50f), result[1].points.map { it.x })
        assertEquals(2, result[0].pressures.size)
        assertTrue(result.none { it.id == stroke.id })
    }

    @Test
    fun erasingAwayFromEveryStrokeReturnsNull() {
        assertNull(StrokeGeometry.erase(listOf(line(1, 0f, 10f)), Offset(500f, 500f), radius = 5f))
    }

    @Test
    fun erasingEverythingRemovesTheStroke() {
        val result = StrokeGeometry.erase(listOf(line(1, 0f, 1f, 2f)), Offset(1f, 0f), radius = 10f)!!
        assertTrue(result.isEmpty())
    }

    @Test
    fun eraserRemovesTextItTouches() {
        val text = StrokeData(id = 5, points = listOf(Offset(10f, 10f)), tool = "text", text = "Hi", fontSize = 40f)
        assertTrue(StrokeGeometry.erase(listOf(text), Offset(12f, 12f), radius = 5f)!!.isEmpty())
        assertNull(StrokeGeometry.erase(listOf(text), Offset(400f, 400f), radius = 5f))
    }

    private val image = StrokeData(id = 77, points = listOf(Offset(20f, 20f)), tool = "image", imageName = "a.jpg", imageWidth = 40f, imageHeight = 30f)

    @Test
    fun imagesEraseOnlyWhenTheEraserIsInsideThem() {
        assertNull(StrokeGeometry.erase(listOf(image), Offset(10f, 10f), radius = 5f))
        assertTrue(StrokeGeometry.erase(listOf(image), Offset(30f, 30f), radius = 5f)!!.isEmpty())
    }

    @Test
    fun imagesSelectByCentreAndExtendTheBoundingBox() {
        val result = StrokeGeometry.lassoSelect(listOf(image), square)
        assertEquals(setOf(77L), result.selectedIds)
        assertEquals(listOf(image), result.strokes)
        val box = StrokeGeometry.boundingBox(listOf(image))!!
        assertEquals(60f, box.right, 0f)
        assertEquals(50f, box.bottom, 0f)
    }

    @Test
    fun lassoKeepsIdsOfStrokesWhollyInsideOrOutside() {
        val inside = StrokeData(id = 1, points = listOf(Offset(10f, 10f), Offset(20f, 20f)))
        val outside = StrokeData(id = 2, points = listOf(Offset(200f, 200f), Offset(210f, 210f)))
        val result = StrokeGeometry.lassoSelect(listOf(inside, outside), square)
        assertEquals(setOf(1L), result.selectedIds)
        assertEquals(listOf(inside, outside), result.strokes)
    }

    @Test
    fun lassoSplitsAStrokeThatCrossesTheBoundary() {
        val crossing = StrokeData(id = 3, points = listOf(Offset(50f, 50f), Offset(80f, 50f), Offset(150f, 50f), Offset(180f, 50f)))
        val result = StrokeGeometry.lassoSelect(listOf(crossing), square)
        assertEquals(2, result.strokes.size)
        assertEquals(1, result.selectedIds.size)
        val selected = result.strokes.first { it.id in result.selectedIds }
        assertEquals(listOf(50f, 80f), selected.points.map { it.x })
    }

    @Test
    fun tooFewLassoPointsSelectsNothing() {
        val result = StrokeGeometry.lassoSelect(listOf(line(1, 0f, 5f)), listOf(Offset.Zero, Offset(1f, 1f)))
        assertTrue(result.selectedIds.isEmpty())
    }

    @Test
    fun moveShiftsOnlySelectedStrokes() {
        val a = line(1, 0f, 10f)
        val b = line(2, 0f, 10f)
        val moved = StrokeGeometry.move(listOf(a, b), setOf(2L), Offset(5f, 7f))
        assertEquals(a, moved[0])
        assertEquals(Offset(5f, 7f), moved[1].points[0])
    }

    @Test
    fun boundingBoxIncludesTextHeight() {
        val text = StrokeData(id = 9, points = listOf(Offset(10f, 10f)), tool = "text", text = "x", fontSize = 40f)
        val box = StrokeGeometry.boundingBox(listOf(text, line(1, 0f, 30f)))!!
        assertEquals(0f, box.left, 0f)
        assertEquals(30f, box.right, 0f)
        assertEquals(50f, box.bottom, 0f)
        assertNull(StrokeGeometry.boundingBox(emptyList()))
    }
}
