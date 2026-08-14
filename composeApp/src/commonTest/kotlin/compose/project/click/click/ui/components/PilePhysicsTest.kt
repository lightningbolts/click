package compose.project.click.click.ui.components // pragma: allowlist secret

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks in the *feel* of the Home pile without a Compose harness: square half-screen sizing,
 * layered depth, omnidirectional dismiss/recall, and fan stagger that collapse must share with open.
 */
class PilePhysicsTest {
    @Test
    fun peekOffsetsAreVerticalOnly() {
        var previousY = -1f
        for (layer in 0 until PILE_MAX_VISIBLE_LAYERS) {
            val (x, y) = pilePeekOffsetDp(layer)
            assertEquals(0f, x, "layer $layer should not peek horizontally")
            assertTrue(y > previousY, "layer $layer y offset $y did not increase")
            assertTrue(y >= 0f, "layer $layer peeked up at y=$y")
            previousY = y
        }
        assertEquals(0f to 12f, pilePeekOffsetDp(1))
        assertEquals(0f to 24f, pilePeekOffsetDp(2))
    }

    @Test
    fun peekOffsetsClampBeyondVisibleLayers() {
        val deepest = pilePeekOffsetDp(PILE_MAX_VISIBLE_LAYERS - 1)
        assertEquals(deepest, pilePeekOffsetDp(PILE_MAX_VISIBLE_LAYERS + 5))
        assertEquals(0f to 0f, pilePeekOffsetDp(-3))
    }

    @Test
    fun cardSizeIsRoughlySquareHalfScreen() {
        val screen = 800f
        val card = pileCardSizeDp(screen)
        val cluster = pileClusterHeightDp(card)
        assertTrue(card in 300f..400f, "card $card should be ~44% of 800")
        assertTrue(cluster in 360f..440f, "cluster $cluster should be ~half of 800")
        assertTrue(abs(cluster / screen - PILE_CLUSTER_SCREEN_FRACTION) < 0.08f)
    }

    @Test
    fun topCardHasDeterministicTiltWithinFourDegrees() {
        val tilt = pileCardTiltDeg("saved-1", layer = 0)
        assertEquals(tilt, pileCardTiltDeg("saved-1", layer = 0))
        assertTrue(abs(tilt) <= 4f, "tilt $tilt exceeded the 4 degree cap")
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
        assertEquals(0f, pileDragTiltDeg(0f, 0f, 300f))
        assertTrue(pileDragTiltDeg(150f, 0f, 300f) > 0f)
        assertTrue(pileDragTiltDeg(-150f, 0f, 300f) < 0f)
        assertTrue(pileDragTiltDeg(0f, -150f, 300f) > 0f, "upward drag should tilt like a rightward throw")
        assertEquals(pileDragTiltDeg(300f, 0f, 300f), pileDragTiltDeg(900f, 0f, 300f))
    }

    @Test
    fun depthIncreasesAwayFromTheFront() {
        assertEquals(1f, pileLayerScale(0))
        assertEquals(1f, pileLayerAlpha(0))
        assertEquals(0f, pileLayerDim(0))
        assertTrue(pileLayerScale(2) < pileLayerScale(1))
        assertTrue(pileLayerAlpha(2) < pileLayerAlpha(1))
        assertTrue(pileLayerDim(2) > pileLayerDim(1))
        assertTrue(pileLayerElevationDp(0) > pileLayerElevationDp(2))
        assertEquals(pileLayerScale(PILE_MAX_VISIBLE_LAYERS), pileLayerScale(PILE_MAX_VISIBLE_LAYERS + 8))
    }

    @Test
    fun omnidirectionalSwipePastThresholdDismisses() {
        val size = 300f
        val justOver = size * GlassGestureCommitFraction + 1f
        assertEquals(
            PileSwipeAction.Dismiss,
            pileSwipeAction(justOver, 0f, 0f, 0f, size, canDismiss = true, canRecall = true),
        )
        assertEquals(
            PileSwipeAction.Dismiss,
            pileSwipeAction(0f, -justOver, 0f, 0f, size, canDismiss = true, canRecall = true),
        )
        assertEquals(
            PileSwipeAction.Dismiss,
            pileSwipeAction(justOver, justOver, 0f, 0f, size, canDismiss = true, canRecall = true),
            "diagonal up-right should dismiss",
        )
        assertEquals(
            PileSwipeAction.SpringBack,
            pileSwipeAction(justOver, 0f, 0f, 0f, size, canDismiss = false, canRecall = true),
        )
    }

    @Test
    fun lowerLeftSwipeRecallsPastSharedCommitFraction() {
        val size = 300f
        val justOver = size * GlassGestureCommitFraction + 1f
        assertEquals(
            PileSwipeAction.Recall,
            pileSwipeAction(-justOver, 0f, 0f, 0f, size, canDismiss = true, canRecall = true),
        )
        assertEquals(
            PileSwipeAction.Recall,
            pileSwipeAction(-justOver, justOver, 0f, 0f, size, canDismiss = true, canRecall = true),
        )
        assertEquals(
            PileSwipeAction.Dismiss,
            pileSwipeAction(-justOver, 0f, 0f, 0f, size, canDismiss = true, canRecall = false),
            "left swipe without a card to recall still dismisses",
        )
    }

    @Test
    fun swipeOppositeLastThrowRecalls() {
        val size = 300f
        val justOver = size * GlassGestureCommitFraction + 1f
        assertEquals(
            PileSwipeAction.Recall,
            pileSwipeAction(
                -justOver,
                -justOver,
                0f,
                0f,
                size,
                canDismiss = true,
                canRecall = true,
                lastExitXPx = justOver,
                lastExitYPx = justOver,
            ),
        )
    }

    @Test
    fun belowThresholdSpringsBackEvenWithADirection() {
        val size = 300f
        val short = size * GlassGestureCommitFraction - 8f
        assertEquals(
            PileSwipeAction.SpringBack,
            pileSwipeAction(short, 0f, 0f, 0f, size, canDismiss = true, canRecall = true),
        )
        assertEquals(
            PileSwipeAction.SpringBack,
            pileSwipeAction(0f, 0f, 0f, 0f, size, canDismiss = true, canRecall = true),
        )
    }

    @Test
    fun fastFlickCommitsEvenShortOfTheThreshold() {
        val size = 300f
        val short = 20f
        val flick = GlassGestureFlickVelocityPxPerSec + 10f
        assertEquals(
            PileSwipeAction.Dismiss,
            pileSwipeAction(short, 0f, flick, 0f, size, canDismiss = true, canRecall = true),
        )
        assertEquals(
            PileSwipeAction.Recall,
            pileSwipeAction(-short, 0f, -flick, 0f, size, canDismiss = true, canRecall = true),
        )
        assertEquals(
            PileSwipeAction.Dismiss,
            pileSwipeAction(0f, -short, 0f, -flick, size, canDismiss = true, canRecall = true),
        )
        assertEquals(
            PileSwipeAction.SpringBack,
            pileSwipeAction(short, 0f, -flick, 0f, size, canDismiss = true, canRecall = true),
            "a flick back toward rest should spring back, not commit",
        )
    }

    @Test
    fun exitTargetLeavesTheScreenAlongTheThrownVector() {
        val size = 300f
        val diagonal = pileCardExitTargetPx(40f, 30f, size)
        assertTrue(diagonal.first > 0f && diagonal.second > 0f)
        assertTrue(diagonal.first > size && diagonal.second > size * 0.5f)
        val right = pileCardExitTargetPx(40f, 8f, size)
        assertTrue(right.first > size)
        val up = pileCardExitTargetPx(8f, -40f, size)
        assertTrue(up.second < -size)
        val left = pileCardExitTargetPx(-40f, 4f, size)
        assertTrue(left.first < -size)
    }

    @Test
    fun recallEntersFromReverseOfExitVector() {
        val size = 300f
        val releaseX = 40f
        val releaseY = 30f
        val (exitX, exitY) = pileCardExitTargetPx(releaseX, releaseY, size)
        val (fromX, fromY) = pileRecallEnterFromPx(releaseX, releaseY, size)
        assertEquals(-exitX, fromX, 0.01f)
        assertEquals(-exitY, fromY, 0.01f)
    }

    @Test
    fun rubberBandStretchesPastEightyFivePercentOfRange() {
        val range = 300f
        val inside = range * 0.8f
        assertEquals(inside, pileRubberBandOffset(inside, range))
        val outside = range * 0.95f
        assertTrue(abs(pileRubberBandOffset(outside, range)) > inside)
        assertTrue(abs(pileRubberBandOffset(outside, range)) < outside)
    }

    @Test
    fun fanStaggerIncreasesWithIndexThenCaps() {
        val delays = (0..10).map { pileFanStaggerMillis(it) }
        assertEquals(0, delays.first())
        delays.zipWithNext { a, b -> assertTrue(b >= a, "stagger went backwards: $a then $b") }
        assertEquals(delays.max(), pileFanStaggerMillis(500))
        assertEquals(0, pileFanStaggerMillis(-4))
    }

    @Test
    fun collapseDurationUsesTheSameStaggerAsOpen() {
        val count = 6
        val collapse = pileFanCollapseDurationMillis(count)
        val openLast = pileFanStaggerMillis(count - 1)
        assertTrue(collapse > openLast, "collapse must include spring settle after the last stagger")
        assertEquals(pileFanCollapseDurationMillis(1), pileFanCollapseDurationMillis(0))
    }
}
