package compose.project.click.click.collaboration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Mirrors iOS `previewIsCoarse` and `pixelationReachesEdges` (sizes from those tests). */
class ClickDropPixelationTest {
    @Test
    fun previewIsAtMost480OnItsLongestSide() {
        assertEquals(480 to 240, ClickDropPixelation.previewSize(2000, 1000))
        assertEquals(270 to 480, ClickDropPixelation.previewSize(1080, 1920))
        // Never scaled up.
        assertEquals(400 to 300, ClickDropPixelation.previewSize(400, 300))
        val (w, h) = ClickDropPixelation.previewSize(4032, 3024)
        assertTrue(maxOf(w, h) <= ClickDropPixelation.PREVIEW_MAX_DIMENSION)
    }

    @Test
    fun longestSideHasTwelveBlocks() {
        assertEquals(12, ClickDropPixelation.BLOCKS_PER_SIDE)
        assertEquals(12, ClickDropPixelation.blockCount(480, 480))
        assertEquals(12, ClickDropPixelation.blockCount(333, 333))
        // The short side of a 333×480 photo: 333 × 12 / 480 → 9 blocks (8.3 rounded up).
        assertEquals(9, ClickDropPixelation.blockCount(333, 480))
    }

    @Test
    fun gradientRowCollapsesToTwelveColours() {
        val w = 480
        val h = 240
        val pixels = IntArray(w * h) { i -> (0xFF shl 24) or ((i % w) * 255 / (w - 1) shl 16) }
        val out = ClickDropPixelation.pixelate(pixels, w, h)
        val row = (0 until w).map { out[it] }
        assertEquals(12, row.distinct().size)
        val column = (0 until h).map { out[it * w] }
        assertEquals(1, column.distinct().size)
    }

    @Test
    fun pixelationFillsTheFrameToItsEdges() {
        // Sides that don't divide into the blocks, as most photos' don't.
        val w = 333
        val h = 480
        val black = 0xFF000000.toInt()
        val out = ClickDropPixelation.pixelate(IntArray(w * h) { black }, w, h)
        assertEquals(w * h, out.size)
        for ((x, y) in listOf(0 to 0, 332 to 0, 0 to 479, 332 to 479, 166 to 0, 166 to 479, 0 to 240, 332 to 240)) {
            assertEquals(black, out[y * w + x], "pixel $x,$y")
        }
    }

    @Test
    fun blocksAverageTheirPixels() {
        // 24×2: each block is 2×2 of alternating red and blue → their average.
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val pixels = IntArray(48) { i -> if (i % 2 == 0) red else blue }
        val out = ClickDropPixelation.pixelate(pixels, 24, 2)
        assertTrue(out.all { it == out[0] })
        assertEquals(0x7F, (out[0] shr 16) and 0xFF)
        assertEquals(0x7F, out[0] and 0xFF)
    }
}
