package uk.kayalab.mynotes.data

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.kayalab.mynotes.ui.canvas.StrokeData

class StrokePackingTest {
    @Test
    fun pointsRoundTripExactly() {
        val points = listOf(Offset(0f, 0f), Offset(-12.5f, 1e6f), Offset(3.14159f, -2.71828f))
        assertEquals(points, StrokePacking.unpackPoints(StrokePacking.packPoints(points)))
        assertEquals(24, StrokePacking.packPoints(points).size)
        assertTrue(StrokePacking.unpackPoints(ByteArray(0)).isEmpty())
    }

    @Test
    fun pressuresRoundTripExactly() {
        val values = listOf(0.1f, 0.55f, 1f)
        assertEquals(values, StrokePacking.unpackFloats(StrokePacking.packFloats(values)))
    }

    @Test
    fun entityConversionPreservesEveryField() {
        val stroke = StrokeData(
            id = 42, points = listOf(Offset(1f, 2f), Offset(3f, 4f)), pressures = listOf(0.3f, 0.9f),
            color = "#FF00FF00", strokeWidth = 7f, tool = "brush", text = null, fontSize = 40f, fontFamily = "Serif"
        )
        val entity = StrokeEntity.from(stroke, noteId = 9, ordinal = 3)
        assertEquals(9L, entity.noteId)
        assertEquals(3, entity.ordinal)
        assertEquals(stroke, entity.toStrokeData())
        val text = stroke.copy(id = 43, tool = "text", text = "Hello", points = listOf(Offset(5f, 5f)), pressures = emptyList())
        assertEquals(text, StrokeEntity.from(text, 9, 0).toStrokeData())
        val image = stroke.copy(id = 44, tool = "image", imageName = "x.jpg", imageWidth = 300f, imageHeight = 200f, pressures = emptyList())
        assertEquals(image, StrokeEntity.from(image, 9, 1).toStrokeData())
    }
}
