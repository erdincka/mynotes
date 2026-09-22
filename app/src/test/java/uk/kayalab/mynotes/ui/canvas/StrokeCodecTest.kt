package uk.kayalab.mynotes.ui.canvas

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeCodecTest {
    private val sample = listOf(
        StrokeData(id = 1, points = listOf(Offset(0f, 0f), Offset(10f, 5.5f)), pressures = listOf(0.4f, 0.9f), color = "#FF112233", strokeWidth = 4f),
        StrokeData(id = 2, points = listOf(Offset(3f, 4f)), tool = "text", text = "Hello", fontSize = 48f, fontFamily = "Serif")
    )

    @Test
    fun roundTripPreservesEveryField() {
        val decoded = StrokeCodec.decode(StrokeCodec.encode(sample)).getOrThrow()
        assertEquals(sample, decoded)
    }

    @Test
    fun blankContentIsAnEmptyNote() {
        assertEquals(emptyList<StrokeData>(), StrokeCodec.decode("").getOrThrow())
        assertEquals(emptyList<StrokeData>(), StrokeCodec.decode("   ").getOrThrow())
    }

    @Test
    fun unknownFieldsFromANewerVersionAreIgnored() {
        val json = """[{"id":7,"points":["1.0,2.0","3.0,4.0"],"futureField":{"nested":true}}]"""
        val decoded = StrokeCodec.decode(json).getOrThrow()
        assertEquals(1, decoded.size)
        assertEquals(7L, decoded[0].id)
        assertEquals(Offset(3f, 4f), decoded[0].points[1])
    }

    @Test
    fun garbageIsReportedAsFailureNotAsAnEmptyNote() {
        assertTrue(StrokeCodec.decode("not json at all").isFailure)
        assertTrue(StrokeCodec.decode("""[{"points":["oops"]}]""").isFailure)
    }
}
