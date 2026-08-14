package compose.project.click.click.ui.components // pragma: allowlist secret

import compose.project.click.click.ui.theme.fnv1a32 // pragma: allowlist secret
import kotlin.math.abs

// Pure geometry and threshold math for the Home pile stacks. Kept Compose-free so the feel of the
// gesture is unit-testable: only PileCluster turns these numbers into animations.
//
// Gesture thresholds are *not* re-invented here — they defer to GlassGestureCommitFraction and
// GlassGestureFlickVelocityPxPerSec, the app's single gesture-physics authority.

/** Top card plus three peeking layers. More than that reads as noise at row height. */
const val PILE_MAX_VISIBLE_LAYERS = 4

/** Peek step per layer. Large enough to signal depth, small enough to never cover a title. */
private const val PILE_PEEK_STEP_X_DP = 12f
private const val PILE_PEEK_STEP_Y_DP = 6f

private const val PILE_REST_TILT_MAX_DEG = 4f
private const val PILE_DRAG_TILT_MAX_DEG = 8f
private const val PILE_FAN_STAGGER_STEP_MILLIS = 40
private const val PILE_FAN_STAGGER_MAX_MILLIS = 240

/**
 * Offset in dp of the card at [layer] behind the top card, as (x, y).
 *
 * Always right and down, never left or up: the title block sits at the bottom-start of each card, so
 * peeking in the other direction is what previously buried labels under their neighbours.
 */
fun pilePeekOffsetDp(layer: Int): Pair<Float, Float> {
    val clamped = layer.coerceIn(0, PILE_MAX_VISIBLE_LAYERS - 1)
    return (clamped * PILE_PEEK_STEP_X_DP) to (clamped * PILE_PEEK_STEP_Y_DP)
}

/**
 * Deterministic resting tilt for a card so a stack looks hand-dropped rather than machine-aligned.
 * Seeded from the card id, so it never changes across recompositions or process restarts.
 */
fun pileCardTiltDeg(
    id: String,
    layer: Int,
): Float {
    if (layer <= 0) return 0f
    val unsigned = fnv1a32("$id#$layer").toUInt()
    val steps = (unsigned % 17u).toInt() - 8
    return (steps / 8f) * PILE_REST_TILT_MAX_DEG
}

/** Tilt while dragging, proportional to how far the card has travelled. */
fun pileDragTiltDeg(
    dragPx: Float,
    widthPx: Float,
): Float {
    val width = widthPx.coerceAtLeast(1f)
    val fraction = (dragPx / width).coerceIn(-1f, 1f)
    return fraction * PILE_DRAG_TILT_MAX_DEG
}

/**
 * Whether releasing the top card should send it away and promote the next one, versus springing it
 * back. Mirrors sheet dismiss: past half the width, or a fast flick in the drag direction.
 */
fun shouldAdvancePileCard(
    offsetPx: Float,
    widthPx: Float,
    velocityPxPerSec: Float,
): Boolean {
    if (offsetPx == 0f) return false
    val width = widthPx.coerceAtLeast(1f)
    if (abs(offsetPx) >= width * GlassGestureCommitFraction) return true
    val flickedInDragDirection = velocityPxPerSec * offsetPx > 0f
    return flickedInDragDirection && abs(velocityPxPerSec) >= GlassGestureFlickVelocityPxPerSec
}

/** Where a committed card animates to, just past the edge in the direction it was thrown. */
fun pileCardExitTargetPx(
    offsetPx: Float,
    widthPx: Float,
): Float {
    val width = widthPx.coerceAtLeast(1f)
    return if (offsetPx < 0f) -width * 1.15f else width * 1.15f
}

/** Per-card delay so a fan-out reads as a staggered deal, not a single layout swap. */
fun pileFanStaggerMillis(index: Int): Int =
    (index.coerceAtLeast(0) * PILE_FAN_STAGGER_STEP_MILLIS).coerceAtMost(PILE_FAN_STAGGER_MAX_MILLIS)
