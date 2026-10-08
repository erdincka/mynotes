package uk.kayalab.mynotes.export

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfFileNamesTest {

    @Test
    fun `date is prefixed to names that do not start with one`() {
        assertEquals("20261008-Note 3 Oct 2026 14_02.pdf", pdfFileNameFor("Note 3 Oct 2026 14:02", "20261008"))
    }

    @Test
    fun `date is not prefixed when the name already starts with one`() {
        assertEquals("20261008 - Standup.pdf", pdfFileNameFor("20261008 - Standup", "20261009"))
        assertEquals("20261008.pdf", pdfFileNameFor("20261008", "20261009"))
    }

    @Test
    fun `a longer number is not mistaken for a date`() {
        assertEquals("20261008-123456789 invoice.pdf", pdfFileNameFor("123456789 invoice", "20261008"))
    }

    @Test
    fun `unsafe characters are replaced and a blank name falls back`() {
        assertEquals("20261008-a_b_c.pdf", pdfFileNameFor("a/b:c", "20261008"))
        assertEquals("20261008-note.pdf", pdfFileNameFor("   ", "20261008"))
    }

    @Test
    fun `repeated names are numbered without colliding with existing ones`() {
        val names = listOf("x.pdf", "x.pdf", "X.pdf", "x (2).pdf", "y.pdf")
        assertEquals(listOf("x.pdf", "x (2).pdf", "X (3).pdf", "x (2) (2).pdf", "y.pdf"), uniquePdfFileNames(names))
    }
}
