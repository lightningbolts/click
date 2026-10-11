package compose.project.click.click.collaboration

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pixelation shared by the sender (a gated drop's preview, a shared drop's teaser) and every drop
 * bubble. Mirrors iOS `ClickDropPixelation`: 12 blocks on the longest side, the grid starting at the
 * top-left corner so edge blocks never sample outside the photo.
 */
object ClickDropPixelation {
    /** Blocks per longest side for the pending/ready look. */
    const val BLOCKS_PER_SIDE = 12

    /** A gated preview is all a viewer can download before reveal: at most this many pixels a side. */
    const val PREVIEW_MAX_DIMENSION = 480

    const val PREVIEW_JPEG_QUALITY = 80

    /** The preview's size: the photo scaled down (never up) to fit [maxDimension]. */
    fun previewSize(
        width: Int,
        height: Int,
        maxDimension: Int = PREVIEW_MAX_DIMENSION,
    ): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        val scale = minOf(1.0, maxDimension.toDouble() / max(width, height))
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    /** Block column (or row) of pixel [coordinate]: the longest side gets exactly [blocks] blocks. */
    fun blockIndex(
        coordinate: Int,
        longestSide: Int,
        blocks: Int = BLOCKS_PER_SIDE,
    ): Int = minOf(blocks - 1, (coordinate.toLong() * blocks / longestSide).toInt())

    /** Blocks along a side of [side] pixels when the longest side is [longestSide]. */
    fun blockCount(
        side: Int,
        longestSide: Int,
        blocks: Int = BLOCKS_PER_SIDE,
    ): Int = if (side <= 0) 0 else blockIndex(side - 1, longestSide, blocks) + 1

    /**
     * Pixelates ARGB [pixels] (row-major, [width]×[height]) into blocks of their average colour.
     * Returns a new array the same size, so every pixel, edges included, belongs to a block.
     */
    fun pixelate(
        pixels: IntArray,
        width: Int,
        height: Int,
        blocks: Int = BLOCKS_PER_SIDE,
    ): IntArray {
        require(pixels.size >= width * height) { "pixels too small" }
        if (width <= 0 || height <= 0) return IntArray(0)
        val longest = max(width, height)
        val cols = blockCount(width, longest, blocks)
        val rows = blockCount(height, longest, blocks)
        val sumA = LongArray(cols * rows)
        val sumR = LongArray(cols * rows)
        val sumG = LongArray(cols * rows)
        val sumB = LongArray(cols * rows)
        val count = IntArray(cols * rows)
        val colOf = IntArray(width) { blockIndex(it, longest, blocks) }
        for (y in 0 until height) {
            val row = blockIndex(y, longest, blocks) * cols
            for (x in 0 until width) {
                val c = pixels[y * width + x]
                val b = row + colOf[x]
                sumA[b] += (c ushr 24) and 0xFF
                sumR[b] += (c shr 16) and 0xFF
                sumG[b] += (c shr 8) and 0xFF
                sumB[b] += c and 0xFF
                count[b]++
            }
        }
        val colors =
            IntArray(cols * rows) { b ->
                val n = count[b].coerceAtLeast(1)
                ((sumA[b] / n).toInt() shl 24) or ((sumR[b] / n).toInt() shl 16) or ((sumG[b] / n).toInt() shl 8) or (sumB[b] / n).toInt()
            }
        val out = IntArray(width * height)
        for (y in 0 until height) {
            val row = blockIndex(y, longest, blocks) * cols
            for (x in 0 until width) out[y * width + x] = colors[row + colOf[x]]
        }
        return out
    }
}

/**
 * The gated drop's preview: a small, heavily pixelated JPEG (≤ [ClickDropPixelation.PREVIEW_MAX_DIMENSION]
 * a side, 12 blocks). Null when [bytes] isn't a decodable image.
 */
expect suspend fun clickDropPreviewJpeg(bytes: ByteArray): ByteArray?

/** Pixel width and height of an encoded image, or null. */
expect fun encodedImageSize(bytes: ByteArray): Pair<Int, Int>?

/**
 * Grapheme clusters as people count them (an emoji counts once); used for the caption limit.
 *
 * Pure Kotlin so it counts the same everywhere: `java.text.BreakIterator` on JDK 17 splits emoji
 * modifier and ZWJ sequences, while Android's ICU-backed one doesn't. Covers the parts of UAX #29
 * that captions hit: combining marks, variation selectors, skin tones, ZWJ sequences, flag pairs,
 * keycaps, tag sequences and CRLF. Hangul jamo sequences count per jamo.
 */
fun graphemeCount(text: String): Int {
    var count = 0
    var i = 0
    var previous = -1
    var regionalRun = 0
    while (i < text.length) {
        val cp = codePointAt(text, i)
        val joins =
            when {
                previous == -1 -> false
                previous == 0x0D && cp == 0x0A -> true
                previous == 0x200D -> true
                isRegionalIndicator(cp) -> isRegionalIndicator(previous) && regionalRun % 2 == 1
                else -> isGraphemeExtender(cp)
            }
        if (!joins) count++
        regionalRun = if (isRegionalIndicator(cp)) (if (joins) regionalRun + 1 else 1) else 0
        previous = cp
        i += if (cp > 0xFFFF) 2 else 1
    }
    return count
}

private fun codePointAt(text: String, index: Int): Int {
    val high = text[index]
    if (high.isHighSurrogate() && index + 1 < text.length) {
        val low = text[index + 1]
        if (low.isLowSurrogate()) return ((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
    }
    return high.code
}

private fun isRegionalIndicator(cp: Int): Boolean = cp in 0x1F1E6..0x1F1FF

private fun isGraphemeExtender(cp: Int): Boolean =
    cp == 0x200D ||
        cp == 0x20E3 ||
        cp in 0xFE00..0xFE0F ||
        cp in 0x1F3FB..0x1F3FF ||
        cp in 0xE0020..0xE007F ||
        cp in 0xE0100..0xE01EF ||
        (
            cp <= 0xFFFF &&
                cp.toChar().category.let {
                    it == CharCategory.NON_SPACING_MARK ||
                        it == CharCategory.ENCLOSING_MARK ||
                        it == CharCategory.COMBINING_SPACING_MARK
                }
        )
