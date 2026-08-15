package compose.project.click.click.ui.components // pragma: allowlist secret

import compose.project.click.click.ui.theme.fnv1a32 // pragma: allowlist secret
import kotlin.math.abs
import kotlin.math.hypot

// Pure geometry and threshold math for the Home photo pile. Kept Compose-free so the feel of
// the gesture is unit-testable: only PhotoPileStack turns these numbers into animations.
//
// Dismiss commits at 200 dp of travel or 800 dp/s of fling. Swipe-down recalls the LIFO
// history stack. Resting tilt is a seeded ±15° hash of the card id.

/** Top card plus two peeking layers. More than that reads as noise at row height. */
const val PILE_MAX_VISIBLE_LAYERS = 3

/** Cluster row occupies roughly half the phone screen. */
const val PILE_CLUSTER_SCREEN_FRACTION = 0.5f

/** Leave a little room for peek + breathing so the square card still dominates the row. */
const val PILE_CARD_SCREEN_FRACTION = 0.44f

/** Vertical peek step per layer (0 / 12 / 24 dp). Titles stay visible because x never shifts. */
private const val PILE_PEEK_STEP_Y_DP = 12f

/** Seeded rest tilt: hash(id) % 31 − 15, inclusive ±15°. */
const val PILE_REST_TILT_MAX_DEG = 15f

/** Active drag tilt: translationX × this factor (degrees per px at mdpi). */
const val PILE_DRAG_TILT_PER_PX = 0.05f

private const val PILE_LAYER_SCALE_STEP = 0.05f
private const val PILE_LAYER_SCALE_MIN = 0.90f
private const val PILE_LAYER_ALPHA_STEP = 0.1f
private const val PILE_LAYER_ALPHA_MIN = 0.7f
private const val PILE_LAYER_DIM_STEP = 0.12f
private const val PILE_LAYER_DIM_MAX = 0.36f
private const val PILE_FRONT_ELEVATION_DP = 16f
private const val PILE_SECOND_ELEVATION_DP = 8f
private const val PILE_BACK_ELEVATION_DP = 4f

/** Playful tap feedback. Fan/carousel open is intentionally disabled. */
const val PILE_TAP_JIGGLE_SCALE = 1.05f
const val PILE_TAP_JIGGLE_WOBBLE_DEG = 5f

/** Rubber-band tension begins past this fraction of card size. */
private const val PILE_RUBBER_BAND_START_FRACTION = 0.85f

/** Alpha starts decaying once Euclidean drag exceeds this (dp). */
const val PILE_ALPHA_FADE_START_DP = 150f

/** Fade completes over this additional distance (dp). */
const val PILE_ALPHA_FADE_RANGE_DP = 250f

/** Commit a dismiss when travel exceeds this (dp), independent of card size. */
const val PILE_DISMISS_DISTANCE_DP = 200f

/** Commit a dismiss when fling speed exceeds this (dp/s). Matches the shared 800 flick. */
const val PILE_FLING_VELOCITY_DP_PER_SEC = 800f

private const val PILE_FAN_STAGGER_STEP_MILLIS = 40
private const val PILE_FAN_STAGGER_MAX_MILLIS = 240
private const val PILE_FAN_SPRING_SETTLE_MILLIS = 420

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
 * Deterministic resting tilt so a stack looks hand-dropped rather than machine-aligned.
 *
 * `rotation = (hash(id) mod 31) − 15`, giving a stable angle in [−15°, +15°].
 */
fun pileCardTiltDeg(
    id: String,
    layer: Int = 0,
): Float {
    val unsigned = fnv1a32("$id#$layer").toUInt()
    return (unsigned % 31u).toInt() - PILE_REST_TILT_MAX_DEG.toInt().toFloat()
}

/** Tilt while dragging: 1:1 translation, `ΔX × 0.05` degrees around the touch anchor. */
@Suppress("UNUSED_PARAMETER")
fun pileDragTiltDeg(
    dragXPx: Float,
    dragYPx: Float = 0f,
    sizePx: Float = 1f,
): Float = dragXPx * PILE_DRAG_TILT_PER_PX

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

fun pileLayerElevationDp(layer: Int): Float =
    when (layer.coerceAtLeast(0)) {
        0 -> PILE_FRONT_ELEVATION_DP
        1 -> PILE_SECOND_ELEVATION_DP
        else -> PILE_BACK_ELEVATION_DP
    }

/**
 * Distance-based fade for the active card.
 *
 * `α = clamp(1 − (D − 150) / 250, 0, 1)` with D in dp.
 */
fun pileDragAlpha(distanceDp: Float): Float {
    val excess = distanceDp - PILE_ALPHA_FADE_START_DP
    if (excess <= 0f) return 1f
    return (1f - excess / PILE_ALPHA_FADE_RANGE_DP).coerceIn(0f, 1f)
}

/**
 * Swipe-up / away dismisses; swipe-down recalls the LIFO history. Commits when travel exceeds
 * [dismissDistancePx] or fling speed exceeds [flingVelocityPxPerSec] in the swipe direction.
 */
@Suppress("UNUSED_PARAMETER")
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
    dismissDistancePx: Float = PILE_DISMISS_DISTANCE_DP,
    flingVelocityPxPerSec: Float = PILE_FLING_VELOCITY_DP_PER_SEC,
): PileSwipeAction {
    if (offsetXPx == 0f &&
        offsetYPx == 0f &&
        hypot(velocityXPxPerSec, velocityYPxPerSec) < flingVelocityPxPerSec
    ) {
        return PileSwipeAction.SpringBack
    }
    val distance = pileDragDistancePx(offsetXPx, offsetYPx)
    val pastDistance = distance >= dismissDistancePx
    val velocityMag = hypot(velocityXPxPerSec, velocityYPxPerSec)
    val flickedInSwipeDirection =
        offsetXPx * velocityXPxPerSec + offsetYPx * velocityYPxPerSec > 0f ||
            (offsetXPx == 0f && offsetYPx == 0f && velocityMag >= flingVelocityPxPerSec)
    val flicked = flickedInSwipeDirection && velocityMag >= flingVelocityPxPerSec
    if (!pastDistance && !flicked) return PileSwipeAction.SpringBack

    val downward = offsetYPx > abs(offsetXPx) && offsetYPx > 0f
    val hasLastExit = lastExitXPx != 0f || lastExitYPx != 0f
    val oppositeLastThrow =
        hasLastExit &&
            offsetXPx * lastExitXPx + offsetYPx * lastExitYPx < 0f
    if (canRecall && (downward || oppositeLastThrow)) {
        return PileSwipeAction.Recall
    }
    return if (canDismiss) PileSwipeAction.Dismiss else PileSwipeAction.SpringBack
}

/**
 * Where a dismissed card animates to. Radial travel is `1.15 × √2 × size` so a 45° throw
 * clears both card axes instead of leaving a corner on-screen.
 */
fun pileCardExitTargetPx(
    offsetXPx: Float,
    offsetYPx: Float,
    sizePx: Float,
): Pair<Float, Float> {
    val size = sizePx.coerceAtLeast(1f)
    val mag = hypot(offsetXPx, offsetYPx).coerceAtLeast(1f)
    val travel = hypot(size, size) * 1.15f
    val scale = travel / mag
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

/** Per-card delay so a fan-out (kept for Reduce-Motion-safe collapse timing) reads as a deal. */
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
