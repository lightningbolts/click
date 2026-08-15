@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.platform.rememberReduceMotionEnabled // pragma: allowlist secret
import compose.project.click.click.ui.components.cardstack.LazyCardStack // pragma: allowlist secret
import compose.project.click.click.ui.components.cardstack.SwipeDirection // pragma: allowlist secret
import compose.project.click.click.ui.components.cardstack.rememberLazyCardStackState // pragma: allowlist secret
import compose.project.click.click.ui.theme.MotionTokens // pragma: allowlist secret
import kotlin.math.hypot

data class PilePhoto(
    /** Unique within its cluster; used for list keys and animation identity. */
    val id: String,
    /**
     * Seed for [generateCardVisual]. Defaults to [id] but should
     * be the raw entity id (e.g. `beacon.id`) so the same item keeps one gradient across every
     * surface that renders it.
     */
    val visualId: String = id,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val categoryBadge: String? = null,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    val content: (@Composable BoxScope.() -> Unit)? = null,
)

/**
 * Unified Home photo pile: LazyCardStack (Hukumister lineage) for 1:1 drag, velocity dismiss,
 * rewind, and stacked depth. Tap always opens the top card's detail — no jiggle.
 */
@Composable
fun PhotoPileStack(
    photos: List<PilePhoto>,
    modifier: Modifier = Modifier,
    label: String = "I'm down for…",
    photoWidth: Dp? = null,
    photoHeight: Dp? = null,
) {
    if (photos.isEmpty()) return
    val density = LocalDensity.current
    val screenHeightPx =
        LocalWindowInfo.current.containerSize.height
            .coerceAtLeast(1)
    val screenHeightDp = with(density) { screenHeightPx.toDp().value }
    val sized = pileCardSizeDp(screenHeightDp).dp
    val width = photoWidth ?: sized
    val height = photoHeight ?: sized
    val deepestPeekY = pilePeekOffsetDp(PILE_MAX_VISIBLE_LAYERS - 1).second
    val rowHeight = height + deepestPeekY.dp + 8.dp

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(rowHeight)
                .semantics { contentDescription = label },
    ) {
        PileCollapsedStack(
            photos = photos,
            photoWidth = width,
            photoHeight = height,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

/**
 * @deprecated Fan/carousel expand is disabled. Prefer [PhotoPileStack].
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PileCluster(
    clusterId: String,
    label: String,
    photos: List<PilePhoto>,
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    photoWidth: Dp? = null,
    photoHeight: Dp? = null,
) {
    PhotoPileStack(
        photos = photos,
        modifier = modifier,
        label = label,
        photoWidth = photoWidth,
        photoHeight = photoHeight,
    )
}

@Composable
private fun PileCollapsedStack(
    photos: List<PilePhoto>,
    photoWidth: Dp,
    photoHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotionEnabled()
    val density = LocalDensity.current
    val cardSizePx = with(density) { minOf(photoWidth, photoHeight).toPx() }
    val flingVelocityPx = with(density) { PILE_FLING_VELOCITY_DP_PER_SEC.dp.toPx() }
    val dismissDistancePx = with(density) { PILE_DISMISS_DISTANCE_DP.dp.toPx() }
    val photoById = remember(photos) { photos.associateBy { it.id } }
    val photoIds = remember(photos) { photos.map { it.id } }

    val state =
        rememberLazyCardStackState(
            horizontalThreshold = { dismissDistancePx },
            verticalThreshold = { dismissDistancePx },
            velocityThresholdPx = flingVelocityPx,
            animationSpec = MotionTokens.pileSnapSpec<androidx.compose.ui.geometry.Offset>(),
        )

    LazyCardStack(
        itemCount = photoIds.size,
        state = state,
        maxVisibleCards = PILE_MAX_VISIBLE_LAYERS,
        modifier =
            modifier
                .width(photoWidth)
                .height(photoHeight + pilePeekOffsetDp(PILE_MAX_VISIBLE_LAYERS - 1).second.dp),
        shouldRewind = { direction ->
            direction == SwipeDirection.Down ||
                (direction == SwipeDirection.Left && state.visibleItemIndex > 0)
        },
        onSwipedItem = { _, _ ->
            PlatformHapticsPolicy.lightImpact()
        },
        onTopCardTap = {
            val top = photoIds.getOrNull(state.visibleItemIndex)
            if (top != null) {
                PlatformHapticsPolicy.lightImpact()
                photoById[top]?.onClick?.invoke()
            }
        },
    ) { index, layer ->
        val photo = photoById[photoIds[index]] ?: return@LazyCardStack
        val isTop = layer == 0
        val (peekX, peekY) = pilePeekOffsetDp(layer)
        val restTilt = if (reduceMotion) 0f else pileCardTiltDeg(photo.id, layer)
        val dragProgress =
            if (isTop) {
                0f
            } else {
                (hypot(state.offset.x, state.offset.y) / cardSizePx.coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
            }
        val scale =
            if (reduceMotion) {
                1f
            } else {
                val resting = pileLayerScale(layer)
                val promoted = pileLayerScale((layer - 1).coerceAtLeast(0))
                resting + (promoted - resting) * dragProgress
            }
        PhotoCard(
            id = photo.visualId,
            title = if (isTop) photo.title else null,
            subtitle = if (isTop) photo.subtitle else null,
            imageUrl = photo.imageUrl,
            categoryBadge = if (isTop) photo.categoryBadge else null,
            elevation = pileLayerElevationDp(layer).dp,
            dimming = if (reduceMotion) 0f else pileLayerDim(layer) * (1f - dragProgress),
            modifier =
                Modifier
                    .offset(x = peekX.dp, y = peekY.dp)
                    .width(photoWidth)
                    .height(photoHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha =
                            if (reduceMotion) {
                                1f
                            } else if (isTop) {
                                pileLayerAlpha(layer) *
                                    pileDragAlpha(
                                        with(density) {
                                            hypot(state.offset.x, state.offset.y).toDp().value
                                        },
                                    )
                            } else {
                                pileLayerAlpha(layer)
                            }
                        if (isTop) {
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            translationX = state.offset.x
                            translationY = state.offset.y
                            rotationZ =
                                if (reduceMotion) {
                                    0f
                                } else {
                                    restTilt + state.rotation
                                }
                        } else {
                            rotationZ = restTilt
                        }
                    },
            onClick = null,
            content = if (isTop) photo.content else null,
        )
    }
}
