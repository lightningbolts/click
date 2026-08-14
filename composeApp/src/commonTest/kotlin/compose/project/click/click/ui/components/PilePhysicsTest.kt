package compose.project.click.click.ui.components // pragma: allowlist secret

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks in the *feel* of the Home pile without a Compose harness: the geometry that keeps titles
 * visible, the tilt that keeps stacks from looking machine-aligned, and the release thresholds that
 * must stay in step with the app's shared gesture physics.
 */
class PilePhysicsTest {
    @Test
    fun peekOffsetsGrowRightAndDownOnly() {
        var previousX = -1f
        var previousY = -1f
        for (layer in 0 until PILE_MAX_VISIBLE_LAYERS) {
            val (x, y) = pilePeekOffsetDp(layer)
            assertTrue(x > previousX, "layer $layer x offset $x did not increase")
            assertTrue(y > previousY, "layer $layer y offset $y did not increase")
            assertTrue(x >= 0f && y >= 0f, "layer $layer peeked up/left at ($x, $y)")
            previousX = x
            previousY = y
        }
    }

    @Test
    fun peekOffsetsClampBeyondVisibleLayers() {
        val deepest = pilePeekOffsetDp(PILE_MAX_VISIBLE_LAYERS - 1)
        assertEquals(deepest, pilePeekOffsetDp(PILE_MAX_VISIBLE_LAYERS + 5))
        assertEquals(0f to 0f, pilePeekOffsetDp(-3))
    }

    @Test
    fun topCardIsNeverTilted() {
        assertEquals(0f, pileCardTiltDeg("saved-1", layer = 0))
    }

    @Test
    fun restTiltIsDeterministicAndClamped() {
        val a = pileCardTiltDeg("saved-1", layer = 2)
        assertEquals(a, pileCardTiltDeg("saved-1", layer = 2))
        assertTrue(abs(a) <= 4f, "tilt $a exceeded the 4 degree cap")
        val other = pileCardTiltDeg("reconnect-1", layer = 2)
        assertTrue(a != other || abs(other) <= 4f)
    }

    @Test
    fun dragTiltFollowsTravelAndSaturates() {
        assertEquals(0f, pileDragTiltDeg(0f, 300f))
        assertTrue(pileDragTiltDeg(150f, 300f) > 0f)
        assertTrue(pileDragTiltDeg(-150f, 300f) < 0f)
        assertEquals(pileDragTiltDeg(300f, 300f), pileDragTiltDeg(900f, 300f))
    }

    @Test
    fun advanceMatchesSharedCommitFraction() {
        val width = 300f
        val justUnder = width * GlassGestureCommitFraction - 1f
        val justOver = width * GlassGestureCommitFraction + 1f
        assertFalse(shouldAdvancePileCard(justUnder, width, velocityPxPerSec = 0f))
        assertTrue(shouldAdvancePileCard(justOver, width, velocityPxPerSec = 0f))
        assertTrue(shouldAdvancePileCard(-justOver, width, velocityPxPerSec = 0f))
    }

    @Test
    fun fastFlickAdvancesEvenShortOfTheThreshold() {
        val width = 300f
        val shortDrag = 20f
        assertTrue(
            shouldAdvancePileCard(shortDrag, width, GlassGestureFlickVelocityPxPerSec + 10f),
            "a fast flick in the drag direction should commit",
        )
        assertFalse(
            shouldAdvancePileCard(shortDrag, width, -(GlassGestureFlickVelocityPxPerSec + 10f)),
            "a flick back toward rest should spring back, not advance",
        )
        assertFalse(shouldAdvancePileCard(0f, width, GlassGestureFlickVelocityPxPerSec * 4f))
    }

    @Test
    fun exitTargetLeavesTheScreenInTheThrownDirection() {
        val width = 300f
        assertTrue(pileCardExitTargetPx(40f, width) > width)
        assertTrue(pileCardExitTargetPx(-40f, width) < -width)
    }

    @Test
    fun fanStaggerIncreasesWithIndexThenCaps() {
        val delays = (0..10).map { pileFanStaggerMillis(it) }
        assertEquals(0, delays.first())
        delays.zipWithNext { a, b -> assertTrue(b >= a, "stagger went backwards: $a then $b") }
        assertEquals(delays.max(), pileFanStaggerMillis(500))
        assertEquals(0, pileFanStaggerMillis(-4))
    }
}
