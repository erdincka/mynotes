package uk.kayalab.mynotes.export

import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil

/** A4 page geometry and the content-to-page mapping, kept pure so it can be unit tested. */
data class PdfLayout(
    val scale: Float,
    val pageCount: Int,
    val originX: Float,
    val originY: Float
) {
    /** Content-space height that fits on one page at this scale. */
    val pageContentHeight: Float get() = USABLE_HEIGHT / scale

    fun pageX(x: Float): Float = (x - originX) * scale + MARGIN
    fun pageY(y: Float, pageIndex: Int): Float =
        (y - originY - pageIndex * pageContentHeight) * scale + MARGIN

    companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 20f
        const val USABLE_WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val USABLE_HEIGHT = PAGE_HEIGHT - 2 * MARGIN

        /**
         * Fits content to the page width, but never enlarges it beyond what a canvas
         * [referenceWidth] pixels wide would need, so a small sketch stays small.
         */
        fun compute(bounds: Rect, referenceWidth: Float): PdfLayout {
            val effectiveWidth = maxOf(bounds.width, referenceWidth, 1f)
            val scale = USABLE_WIDTH / effectiveWidth
            val scaledHeight = bounds.height * scale
            val pages = ceil(scaledHeight / USABLE_HEIGHT).toInt().coerceAtLeast(1)
            return PdfLayout(scale = scale, pageCount = pages, originX = bounds.left, originY = bounds.top)
        }
    }
}
