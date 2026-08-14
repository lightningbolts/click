@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.platform.rememberReduceMotionEnabled // pragma: allowlist secret
import compose.project.click.click.ui.theme.MotionTokens // pragma: allowlist secret
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PilePhoto(
    /** Unique within its cluster; used for list keys and animation identity. */
    val id: String,
    /**
     * Seed for [compose.project.click.click.ui.theme.generateCardVisual]. Defaults to [id] but should
     * be the raw entity id (e.g. `beacon.id`) so the same item keeps one gradient across every
     * surface that renders it.
     */
    val visualId: String = id,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    val content: (@Composable BoxScope.() -> Unit)? = null,
)

private val PileDragElevation = 16.dp
private val PileRestElevation = 6.dp
private val PileFanSpacing = 10.dp
private val PileRowBottomBreathingRoom = 8.dp

/**
 * One pile cluster as a full-width row: a peeking stack of Polaroids that fans out in place.
 *
 * Collapsed, the top card is draggable with spring physics — tilt proportional to travel, raised
 * elevation while held, spring-back under the commit threshold, and a throw-off that promotes the
 * next card with a haptic tick. Expanded, cards deal out on a staggered spring rather than snapping
 * into a new layout. Reduce Motion swaps all of that for a plain fade.
 *
 * Cards only ever peek right and down so the top card's title is never covered, and each cluster owns
 * its own row, so neighbouring clusters cannot collide the way the old free-floating board did.
 */
@Composable
fun PileCluster(
    clusterId: String,
    label: String,
    photos: List<PilePhoto>,
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    photoWidth: Dp = 156.dp,
    photoHeight: Dp = 188.dp,
) {
    if (photos.isEmpty()) return
    val (deepestPeekX, deepestPeekY) = pilePeekOffsetDp(PILE_MAX_VISIBLE_LAYERS - 1)
    val rowHeight = photoHeight + deepestPeekY.dp + PileRowBottomBreathingRoom
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(rowHeight)
                .semantics { contentDescription = label },
    ) {
        if (expanded) {
            PileFanStrip(
                photos = photos,
                photoWidth = photoWidth,
                photoHeight = photoHeight,
                onCollapse = onCollapse,
                modifier = Modifier.align(Alignment.TopStart),
            )
        } else {
            PileCollapsedStack(
                clusterId = clusterId,
                photos = photos,
                photoWidth = photoWidth,
                photoHeight = photoHeight,
                stackWidth = photoWidth + deepestPeekX.dp,
                onExpand = onExpand,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

@Composable
private fun PileCollapsedStack(
    clusterId: String,
    photos: List<PilePhoto>,
    photoWidth: Dp,
    photoHeight: Dp,
    stackWidth: Dp,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotionEnabled()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragOffset = remember(clusterId) { Animatable(0f) }
    var topIndex by remember(clusterId) { mutableIntStateOf(0) }
    var isDragging by remember(clusterId) { mutableStateOf(false) }
    val cardWidthPx = with(density) { photoWidth.toPx() }
    val canCycle = photos.size > 1
    val elevation by animateDpAsState(
        targetValue = if (isDragging) PileDragElevation else PileRestElevation,
        animationSpec = MotionTokens.softEnterSpec(),
        label = "pile_elevation",
    )

    val draggableState =
        rememberDraggableState { delta ->
            scope.launch { dragOffset.snapTo(dragOffset.value + delta) }
        }

    Box(
        modifier =
            modifier
                .width(stackWidth)
                .then(
                    if (canCycle) {
                        Modifier.draggable(
                            state = draggableState,
                            orientation = Orientation.Horizontal,
                            onDragStarted = { isDragging = true },
                            onDragStopped = { velocity ->
                                isDragging = false
                                if (shouldAdvancePileCard(dragOffset.value, cardWidthPx, velocity)) {
                                    dragOffset.animateTo(
                                        targetValue = pileCardExitTargetPx(dragOffset.value, cardWidthPx),
                                        animationSpec = MotionTokens.softExitSpec(),
                                    )
                                    topIndex = (topIndex + 1) % photos.size
                                    PlatformHapticsPolicy.lightImpact()
                                    dragOffset.snapTo(0f)
                                } else {
                                    dragOffset.animateTo(0f, MotionTokens.softEnterSpec())
                                }
                            },
                        )
                    } else {
                        Modifier
                    },
                ),
    ) {
        val visibleLayers = minOf(photos.size, PILE_MAX_VISIBLE_LAYERS)
        // Draw back-to-front so the interactive top card ends up on top and owns hit testing.
        for (reverseLayer in 0 until visibleLayers) {
            val layer = visibleLayers - 1 - reverseLayer
            val photo = photos[(topIndex + layer) % photos.size]
            val (peekX, peekY) = pilePeekOffsetDp(layer)
            val isTop = layer == 0
            val restTilt = if (reduceMotion) 0f else pileCardTiltDeg(photo.id, layer)
            PhotoCard(
                id = photo.visualId,
                title = if (isTop) photo.title else null,
                subtitle = if (isTop) photo.subtitle else null,
                imageUrl = photo.imageUrl,
                elevation = if (isTop) elevation else PileRestElevation,
                modifier =
                    Modifier
                        .offset(x = peekX.dp, y = peekY.dp)
                        .width(photoWidth)
                        .height(photoHeight)
                        .zIndex((visibleLayers - layer).toFloat())
                        .graphicsLayer {
                            if (isTop) {
                                translationX = dragOffset.value
                                rotationZ =
                                    if (reduceMotion) {
                                        0f
                                    } else {
                                        pileDragTiltDeg(dragOffset.value, size.width)
                                    }
                            } else {
                                rotationZ = restTilt
                            }
                        },
                onClick = if (isTop) onExpand else null,
                content = if (isTop) photo.content else null,
            )
        }
    }
}

@Composable
private fun PileFanStrip(
    photos: List<PilePhoto>,
    photoWidth: Dp,
    photoHeight: Dp,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotionEnabled()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(PileFanSpacing),
        verticalAlignment = Alignment.Top,
    ) {
        photos.forEachIndexed { index, photo ->
            val appear = remember(photo.id) { Animatable(if (reduceMotion) 1f else 0f) }
            LaunchedEffect(photo.id, reduceMotion) {
                if (reduceMotion) {
                    appear.animateTo(1f, tween(durationMillis = 120))
                } else {
                    delay(pileFanStaggerMillis(index).toLong())
                    appear.animateTo(1f, MotionTokens.softEnterSpec())
                }
            }
            PhotoCard(
                id = photo.visualId,
                title = photo.title,
                subtitle = photo.subtitle,
                imageUrl = photo.imageUrl,
                modifier =
                    Modifier
                        .width(photoWidth)
                        .height(photoHeight)
                        .graphicsLayer {
                            val progress = appear.value
                            alpha = progress
                            if (!reduceMotion) {
                                scaleX = 0.9f + 0.1f * progress
                                scaleY = 0.9f + 0.1f * progress
                                translationY = (1f - progress) * 28f
                            }
                        },
                onClick = photo.onClick,
                onLongClick = photo.onLongClick,
                content = photo.content,
            )
        }
        PileFanCollapseSpacer(onCollapse = onCollapse, height = photoHeight)
    }
}

/** Trailing tap target so a fan can be dismissed without reaching for the header or system back. */
@Composable
private fun PileFanCollapseSpacer(
    onCollapse: () -> Unit,
    height: Dp,
) {
    Box(
        modifier =
            Modifier
                .width(48.dp)
                .height(height)
                .pointerInput(onCollapse) {
                    detectTapGestures(onTap = { onCollapse() })
                },
    )
}
