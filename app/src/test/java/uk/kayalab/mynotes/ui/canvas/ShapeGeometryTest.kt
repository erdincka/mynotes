package uk.kayalab.mynotes.ui.canvas

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeGeometryTest {
    private val a = Offset(10f, 20f)
    private val b = Offset(110f, 80f)

    @Test
    fun lineIsJustItsEndpoints() {
        assertEquals(listOf(a, b), ShapeGeometry.points(ShapeKind.LINE, a, b))
    }

    @Test
    fun rectangleIsClosedAndAxisAligned() {
        val pts = ShapeGeometry.points(ShapeKind.RECTANGLE, a, b)
        assertEquals(5, pts.size)
        assertEquals(pts.first(), pts.last())
        assertEquals(setOf(10f, 110f), pts.map { it.x }.toSet())
        assertEquals(setOf(20f, 80f), pts.map { it.y }.toSet())
    }

    @Test
    fun ellipseFitsInsideTheBoundingBox() {
        val pts = ShapeGeometry.points(ShapeKind.ELLIPSE, a, b)
        assertEquals(pts.first(), pts.last())
        assertTrue(pts.all { it.x >= 10f - 1e-3f && it.x <= 110f + 1e-3f && it.y >= 20f - 1e-3f && it.y <= 80f + 1e-3f })
        assertEquals(110f, pts.maxOf { it.x }, 1e-3f)
        assertEquals(20f, pts.minOf { it.y }, 1e-3f)
    }

    @Test
    fun arrowEndsAtTheTipWithTwoHeadStrokes() {
        val pts = ShapeGeometry.points(ShapeKind.ARROW, Offset(0f, 0f), Offset(100f, 0f), strokeWidth = 5f)
        assertEquals(5, pts.size)
        assertEquals(Offset(100f, 0f), pts[1])
        assertEquals(Offset(100f, 0f), pts[3])
        assertTrue(pts[2].x < 100f && pts[4].x < 100f)
        assertTrue(pts[2].y < 0f != pts[4].y < 0f)
    }
}
