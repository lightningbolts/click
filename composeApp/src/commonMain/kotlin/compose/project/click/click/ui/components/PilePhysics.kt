package compose.project.click.click.ui.components // pragma: allowlist secret

import compose.project.click.click.ui.theme.fnv1a32 // pragma: allowlist secret
import kotlin.math.abs
import kotlin.math.hypot

// Pure geometry and threshold math for the Home pile stacks. Kept Compose-free so the feel of the
// gesture is unit-testable: only PileCluster turns these numbers into animations.
//
// Gesture *thresholds* are not re-invented here — they defer to GlassGestureCommitFraction and
// GlassGestureFlickVelocityPxPerSec, the app's single gesture-physics authority. What this file
// owns is the 2D interaction model: 1:1 drag tracking, omnidirectional dismiss vs recall, depth by
// layer, and the staggered fan timing.

/** Top card plus two peeking layers. More than that reads as noise at row height. */
const val PILE_MAX_VISIBLE_LAYERS = 3

/** Cluster row occupies roughly half the phone screen. */
const val PILE_CLUSTER_SCREEN_FRACTION = 0.5f

/** Leave a little room for peek + breathing so the square card still dominates the row. */
const val PILE_CARD_SCREEN_FRACTION = 0.44f

/** Vertical peek step per layer (0 / 12 / 24 dp). Titles stay visible because x never shifts. */
private const val PILE_PEEK_STEP_Y_DP = 12f

private const val PILE_REST_TILT_MAX_DEG = 4f
private const val PILE_DRAG_TILT_MAX_DEG = 10f
private const val PILE_FAN_STAGGER_STEP_MILLIS = 40
private const val PILE_FAN_STAGGER_MAX_MILLIS = 240
private const val PILE_FAN_SPRING_SETTLE_MILLIS = 420

private const val PILE_LAYER_SCALE_STEP = 0.05f
private const val PILE_LAYER_SCALE_MIN = 0.85f
private const val PILE_LAYER_ALPHA_STEP = 0.1f
private const val PILE_LAYER_ALPHA_MIN = 0.7f
private const val PILE_LAYER_DIM_STEP = 0.12f
private const val PILE_LAYER_DIM_MAX = 0.36f
private const val PILE_FRONT_ELEVATION_DP = 18f
private const val PILE_LAYER_ELEVATION_STEP_DP = 4f
private const val PILE_BACK_ELEVATION_DP = 4f

/** Playful tap feedback before fan-out. */
const val PILE_TAP_JIGGLE_SCALE = 1.02f
const val PILE_TAP_JIGGLE_WOBBLE_DEG = 2f

/** Rubber-band tension begins past this fraction of card size. */
private const val PILE_RUBBER_BAND_START_FRACTION = 0.85f

enum class PileSwipeAction {
    SpringBack,
    Dismiss,
    Recall,
}

/**
 * Offset in dp of the card at [layer] behind the top card, as (x, y).
 *
 * Vertical-only peek (x = 0): the title block sits at the bottom-start of each card, so horizontal
 * peek previously buried labels under their neighbours.
 */
fun pilePeekOffsetDp(layer: Int): Pair<Float, Float> {
    val clamped = layer.coerceIn(0, PILE_MAX_VISIBLE_LAYERS - 1)
    return 0f to (clamped * PILE_PEEK_STEP_Y_DP)
}

/**
 * Square card size in dp so the cluster (card + peek) occupies roughly half the screen height.
 */
fun pileCardSizeDp(screenHeightDp: Float): Float {
    val height = screenHeightDp.coerceAtLeast(1f)
    val fromFraction = height * PILE_CARD_SCREEN_FRACTION
    val clusterBudget = height * PILE_CLUSTER_SCREEN_FRACTION
    val peekY = pilePeekOffsetDp(PILE_MAX_VISIBLE_LAYERS - 1).second
    val fromCluster = clusterBudget - peekY - 8f
    return minOf(fromFraction, fromCluster).coerceAtLeast(height * 0.4f)
}

fun pileClusterHeightDp(cardSizeDp: Float): Float {
    val peekY = pilePeekOffsetDp(PILE_MAX_VISIBLE_LAYERS - 1).second
    return cardSizeDp + peekY + 8f
}

/**
 * Deterministic resting tilt for a card so a stack looks hand-dropped rather than machine-aligned.
 * Seeded from the card id, so it never changes across recompositions or process restarts.
 */
fun pileCardTiltDeg(
    id: String,
    layer: Int,
): Float {
    val unsigned = fnv1a32("$id#$layer").toUInt()
    val steps = (unsigned % 17u).toInt() - 8
    return (steps / 8f) * PILE_REST_TILT_MAX_DEG
}

/** Tilt while dragging, proportional to how far the card has travelled on the dominant axis. */
fun pileDragTiltDeg(
    dragXPx: Float,
    dragYPx: Float,
    sizePx: Float,
): Float {
    val size = sizePx.coerceAtLeast(1f)
    val dominant = if (abs(dragXPx) >= abs(dragYPx)) dragXPx else -dragYPx
    val fraction = (dominant / size).coerceIn(-1f, 1f)
    return fraction * PILE_DRAG_TILT_MAX_DEG
}

fun pileLayerScale(layer: Int): Float {
    val clamped = layer.coerceAtLeast(0)
    return (1f - clamped * PILE_LAYER_SCALE_STEP).coerceAtLeast(PILE_LAYER_SCALE_MIN)
}

fun pileLayerAlpha(layer: Int): Float {
    val clamped = layer.coerceAtLeast(0)
    return (1f - clamped * PILE_LAYER_ALPHA_STEP).coerceAtLeast(PILE_LAYER_ALPHA_MIN)
}

/** Extra black scrim on back layers so the pile reads as physically stacked. */
fun pileLayerDim(layer: Int): Float {
    val clamped = layer.coerceAtLeast(0)
    return (clamped * PILE_LAYER_DIM_STEP).coerceAtMost(PILE_LAYER_DIM_MAX)
}

fun pileLayerElevationDp(layer: Int): Float {
    val clamped = layer.coerceAtLeast(0)
    return (PILE_FRONT_ELEVATION_DP - clamped * PILE_LAYER_ELEVATION_STEP_DP)
        .coerceAtLeast(PILE_BACK_ELEVATION_DP)
}

/**
 * Omnidirectional swipe-to-dismiss/recall. Any fling/swipe past the shared commit threshold
 * dismisses when possible; recall when the gesture opposes the last throw or enters the lower-left
 * quadrant. Below threshold (or when that action is impossible) springs back. A fast flick in the
 * swipe direction commits even short of half size.
 */
fun pileSwipeAction(
    offsetXPx: Float,
    offsetYPx: Float,
    velocityXPxPerSec: Float,
    velocityYPxPerSec: Float,
    sizePx: Float,
    canDismiss: Boolean,
    canRecall: Boolean,
    lastExitXPx: Float = 0f,
    lastExitYPx: Float = 0f,
): PileSwipeAction {
    if (offsetXPx == 0f &&
        offsetYPx == 0f &&
        abs(velocityXPxPerSec) < GlassGestureFlickVelocityPxPerSec &&
        abs(velocityYPxPerSec) < GlassGestureFlickVelocityPxPerSec
    ) {
        return PileSwipeAction.SpringBack
    }
    val size = sizePx.coerceAtLeast(1f)
    val distance = pileDragDistancePx(offsetXPx, offsetYPx)
    val pastDistance = distance >= size * GlassGestureCommitFraction
    val velocityMag = hypot(velocityXPxPerSec, velocityYPxPerSec)
    val flickedInSwipeDirection =
        offsetXPx * velocityXPxPerSec + offsetYPx * velocityYPxPerSec > 0f ||
            (offsetXPx == 0f && offsetYPx == 0f && velocityMag >= GlassGestureFlickVelocityPxPerSec)
    val flicked = flickedInSwipeDirection && velocityMag >= GlassGestureFlickVelocityPxPerSec
    if (!pastDistance && !flicked) return PileSwipeAction.SpringBack

    val lowerLeftRecall = offsetXPx < 0f && offsetYPx >= 0f
    val hasLastExit = lastExitXPx != 0f || lastExitYPx != 0f
    val oppositeLastThrow =
        hasLastExit &&
            offsetXPx * lastExitXPx + offsetYPx * lastExitYPx < 0f
    if (canRecall && (lowerLeftRecall || oppositeLastThrow)) {
        return PileSwipeAction.Recall
    }
    return if (canDismiss) PileSwipeAction.Dismiss else PileSwipeAction.SpringBack
}

/** Where a dismissed card animates to, just past the edge along the actual 2D throw vector. */
fun pileCardExitTargetPx(
    offsetXPx: Float,
    offsetYPx: Float,
    sizePx: Float,
): Pair<Float, Float> {
    val size = sizePx.coerceAtLeast(1f)
    val mag = hypot(offsetXPx, offsetYPx).coerceAtLeast(1f)
    val scale = size * 1.35f / mag
    return offsetXPx * scale to offsetYPx * scale
}

/** Off-screen start for a recalled card: reverse of the exit trajectory. */
fun pileRecallEnterFromPx(
    offsetXPx: Float,
    offsetYPx: Float,
    sizePx: Float,
): Pair<Float, Float> {
    val (exitX, exitY) = pileCardExitTargetPx(offsetXPx, offsetYPx, sizePx)
    return -exitX to -exitY
}

/**
 * Rubber-band drag past ~85% of [range]: linear inside, diminishing stretch beyond.
 */
fun pileRubberBandOffset(
    offset: Float,
    range: Float,
): Float {
    val limit = range.coerceAtLeast(1f)
    val threshold = limit * PILE_RUBBER_BAND_START_FRACTION
    val absOffset = abs(offset)
    if (absOffset <= threshold) return offset
    val sign = if (offset >= 0f) 1f else -1f
    val excess = absOffset - threshold
    val stretched = threshold + excess * 0.35f
    return sign * stretched.coerceAtMost(absOffset)
}

/** Per-card delay so a fan-out (and matching collapse) reads as a staggered deal. */
fun pileFanStaggerMillis(index: Int): Int =
    (index.coerceAtLeast(0) * PILE_FAN_STAGGER_STEP_MILLIS).coerceAtMost(PILE_FAN_STAGGER_MAX_MILLIS)

/** Collapse uses the same stagger as open, played back-to-front, plus spring settle. */
fun pileFanCollapseDurationMillis(count: Int): Int {
    val lastIndex = (count - 1).coerceAtLeast(0)
    return pileFanStaggerMillis(lastIndex) + PILE_FAN_SPRING_SETTLE_MILLIS
}

fun pileDragDistancePx(
    offsetXPx: Float,
    offsetYPx: Float,
): Float = hypot(offsetXPx, offsetYPx)
