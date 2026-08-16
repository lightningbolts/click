package compose.project.click.click.ui.components // pragma: allowlist secret

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CenterCropRectTest {
    @Test
    fun squareSourceFillsDestination() {
        val crop = centerCropSourceRect(srcWidth = 100f, srcHeight = 100f, dstSize = 44f)
        assertEquals(0f, crop.srcX)
        assertEquals(0f, crop.srcY)
        assertEquals(100f, crop.srcW)
        assertEquals(100f, crop.srcH)
    }

    @Test
    fun landscapeSourceCropsHorizontally() {
        val crop = centerCropSourceRect(srcWidth = 200f, srcHeight = 100f, dstSize = 50f)
        assertEquals(50f, crop.srcX, 0.01f)
        assertEquals(0f, crop.srcY, 0.01f)
        assertEquals(100f, crop.srcW, 0.01f)
        assertEquals(100f, crop.srcH, 0.01f)
    }

    @Test
    fun portraitSourceCropsVertically() {
        val crop = centerCropSourceRect(srcWidth = 80f, srcHeight = 160f, dstSize = 40f)
        assertEquals(0f, crop.srcX, 0.01f)
        assertTrue(abs(crop.srcY - 40f) < 0.01f)
        assertEquals(80f, crop.srcW, 0.01f)
        assertEquals(80f, crop.srcH, 0.01f)
    }
}

private fun assertEquals(
    expected: Float,
    actual: Float,
    absoluteTolerance: Float,
) {
    assertTrue(
        abs(expected - actual) <= absoluteTolerance,
        "expected $expected but was $actual",
    )
}
