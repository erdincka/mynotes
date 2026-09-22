package uk.kayalab.mynotes.export

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfLayoutTest {
    private val screenWidth = 1200f

    @Test
    fun smallSketchIsNotBlownUpToPageWidth() {
        val layout = PdfLayout.compute(Rect(100f, 100f, 200f, 150f), screenWidth)
        assertEquals(PdfLayout.USABLE_WIDTH / screenWidth, layout.scale, 1e-6f)
        assertEquals(1, layout.pageCount)
    }

    @Test
    fun wideContentIsScaledToFitThePage() {
        val layout = PdfLayout.compute(Rect(0f, 0f, 2400f, 100f), screenWidth)
        assertEquals(PdfLayout.USABLE_WIDTH / 2400f, layout.scale, 1e-6f)
        assertEquals(PdfLayout.MARGIN, layout.pageX(0f), 1e-4f)
        assertEquals(PdfLayout.PAGE_WIDTH - PdfLayout.MARGIN, layout.pageX(2400f), 1e-3f)
    }

    @Test
    fun tallContentSpillsOntoFurtherPages() {
        val layout = PdfLayout.compute(Rect(0f, 0f, 1200f, 5000f), screenWidth)
        val scaledHeight = 5000f * layout.scale
        assertEquals(kotlin.math.ceil(scaledHeight / PdfLayout.USABLE_HEIGHT).toInt(), layout.pageCount)
        assertEquals(PdfLayout.MARGIN, layout.pageY(layout.pageContentHeight, pageIndex = 1), 1e-3f)
    }

    @Test
    fun originIsTheTopLeftOfTheContent() {
        val layout = PdfLayout.compute(Rect(-50f, 300f, 50f, 400f), screenWidth)
        assertEquals(PdfLayout.MARGIN, layout.pageX(-50f), 1e-4f)
        assertEquals(PdfLayout.MARGIN, layout.pageY(300f, 0), 1e-4f)
    }
}
