package uk.kayalab.mynotes.ui.canvas

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeOutlineTest {

    @Test
    fun pressureOnlyMattersWhenItActuallyVaries() {
        assertFalse(StrokeOutline.hasVariation(listOf(1f, 1f, 1f)))
        assertFalse(StrokeOutline.hasVariation(listOf(0.5f)))
        assertTrue(StrokeOutline.hasVariation(listOf(0.2f, 0.8f)))
        val highlighter = StrokeData(points = listOf(Offset.Zero, Offset(1f, 1f)), pressures = listOf(0.1f, 0.9f), tool = "highlighter")
        assertFalse(StrokeOutline.usesPressure(highlighter))
        assertTrue(StrokeOutline.usesPressure(highlighter.copy(tool = "pen")))
    }

    @Test
    fun widthGrowsWithPressureAndBrushIsMoreExpressiveThanPen() {
        val penLight = StrokeOutline.widthFor("pen", 10f, 0f)
        val penHard = StrokeOutline.widthFor("pen", 10f, 1f)
        val brushLight = StrokeOutline.widthFor("brush", 10f, 0f)
        val brushHard = StrokeOutline.widthFor("brush", 10f, 1f)
        assertTrue(penLight < penHard)
        assertTrue(brushHard - brushLight > penHard - penLight)
        assertEquals(10f, StrokeOutline.widthFor("highlighter", 10f, 0.3f), 0f)
        assertEquals(StrokeOutline.widthFor("pen", 10f, 1f), StrokeOutline.widthFor("pen", 10f, 5f), 0f)
    }

    @Test
    fun smoothingMovesTowardsTheRawValue() {
        assertEquals(0.5f, StrokeOutline.smoothPressure(null, 0.5f), 0f)
        val next = StrokeOutline.smoothPressure(0.5f, 1f)
        assertTrue(next > 0.5f && next < 1f)
    }

    @Test
    fun resamplingKeepsEndpointsAndAddsPointsBetween() {
        val points = listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(20f, 0f), Offset(30f, 0f))
        val (out, pressures) = StrokeOutline.resample(points, listOf(0f, 0.2f, 0.4f, 0.6f))
        assertEquals(points.first(), out.first())
        assertEquals(points.last(), out.last())
        assertTrue(out.size > points.size)
        assertEquals(out.size, pressures.size)
        assertEquals(0.6f, pressures.last(), 1e-6f)
    }

    @Test
    fun straightLineOutlineHasTheRequestedWidth() {
        val points = listOf(Offset(0f, 0f), Offset(50f, 0f), Offset(100f, 0f))
        val outline = StrokeOutline.build(points, listOf(10f, 10f, 10f))
        val ys = outline.polygon.map { it.y }
        assertEquals(-5f, ys.min(), 1e-4f)
        assertEquals(5f, ys.max(), 1e-4f)
        assertEquals(6, outline.polygon.size)
        assertEquals(2, outline.joints.size)
        assertEquals(5f, outline.joints[0].radius, 0f)
    }

    @Test
    fun sharpTurnsGetAFillerJoint() {
        val hairpin = listOf(Offset(0f, 0f), Offset(50f, 0f), Offset(100f, 0f), Offset(50f, 2f), Offset(0f, 2f))
        val outline = StrokeOutline.build(hairpin, List(5) { 6f })
        assertTrue(outline.joints.size > 2)
    }

    @Test
    fun circlesWindTheSameWayAsThePolygon() {
        val square = listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(10f, 10f), Offset(0f, 10f))
        val circle = StrokeOutline.circlePoints(StrokeOutline.Joint(Offset(5f, 5f), 2f), square)
        val sameSign = StrokeOutline.signedArea(square) * StrokeOutline.signedArea(circle) > 0f
        assertTrue(sameSign)
    }

    @Test
    fun singlePointBecomesADot() {
        val outline = StrokeOutline.build(listOf(Offset(3f, 3f)), listOf(8f))
        assertTrue(outline.polygon.isEmpty())
        assertEquals(1, outline.joints.size)
        assertEquals(4f, outline.joints[0].radius, 0f)
    }
}
