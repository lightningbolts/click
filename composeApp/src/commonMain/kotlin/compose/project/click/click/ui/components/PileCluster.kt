@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.platform.rememberReduceMotionEnabled // pragma: allowlist secret
import compose.project.click.click.ui.theme.MotionTokens // pragma: allowlist secret
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PilePhoto(
    /** Unique within its cluster; used for list keys and animation identity. */
    val id: String,
    /**
     * Seed for [compose.project.click.click.ui.theme.generateCardVisual]. Defaults to [id] but should // pragma: allowlist secret
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

private val PileFanSpacing = 10.dp

/**
 * One pile cluster as a full-width row: a peeking stack of roughly-square Polaroids that fans out
 * in place. The row occupies about half the screen height so the cluster is the dominant visual.
 *
 * Collapsed, the top card tracks the finger 1:1 (no spring lag, no grid). Swipe past the shared
 * commit threshold in any direction flies it off with the swipe velocity; a lower-left swipe (or
 * one opposing the last throw) recalls the most recently dismissed card. Back layers scale down,
 * dim, and drop elevation so the pile reads as physically stacked.
 * Expanded, cards deal out — and collapse back — on the same staggered spring.
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
    var renderFan by remember(clusterId) { mutableStateOf(expanded) }

    LaunchedEffect(clusterId, expanded, photos.size) {
        if (expanded) {
            renderFan = true
        } else if (renderFan) {
            delay(pileFanCollapseDurationMillis(photos.size).toLong())
            renderFan = false
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(rowHeight)
                .semantics { contentDescription = label },
    ) {
        if (renderFan) {
            PileFanStrip(
                photos = photos,
                photoWidth = width,
                photoHeight = height,
                expanded = expanded,
                onCollapse = onCollapse,
                modifier = Modifier.align(Alignment.TopStart),
            )
        } else {
            PileCollapsedStack(
                clusterId = clusterId,
                photos = photos,
                photoWidth = width,
                photoHeight = height,
                stackWidth = width,
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
    val photoIds = remember(photos) { photos.map { it.id } }
    var stackIds by remember(clusterId, photoIds) { mutableStateOf(photoIds) }
    var dismissedIds by remember(clusterId, photoIds) { mutableStateOf(emptyList<String>()) }
    val photoById = remember(photos) { photos.associateBy { it.id } }

    var dragging by remember(clusterId) { mutableStateOf(false) }
    var liveX by remember(clusterId) { mutableFloatStateOf(0f) }
    var liveY by remember(clusterId) { mutableFloatStateOf(0f) }
    var lastExitX by remember(clusterId) { mutableFloatStateOf(0f) }
    var lastExitY by remember(clusterId) { mutableFloatStateOf(0f) }
    var touchAnchorX by remember(clusterId) { mutableFloatStateOf(0.5f) }
    var touchAnchorY by remember(clusterId) { mutableFloatStateOf(0.5f) }
    val animX = remember(clusterId) { Animatable(0f) }
    val animY = remember(clusterId) { Animatable(0f) }
    val jiggleScale = remember(clusterId) { Animatable(1f) }
    val jiggleRot = remember(clusterId) { Animatable(0f) }
    val cardSizePx = with(density) { minOf(photoWidth, photoHeight).toPx() }

    Box(
        modifier =
            modifier
                .width(stackWidth)
                .height(photoHeight + pilePeekOffsetDp(PILE_MAX_VISIBLE_LAYERS - 1).second.dp)
                .pointerInput(clusterId, stackIds, dismissedIds, cardSizePx, reduceMotion) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        touchAnchorX = (down.position.x / cardSizePx).coerceIn(0f, 1f)
                        touchAnchorY = (down.position.y / cardSizePx).coerceIn(0f, 1f)
                        val slopChange =
                            awaitTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                            }
                        if (slopChange == null) {
                            if (!dragging) {
                                scope.launch {
                                    if (!reduceMotion) {
                                        val wobble =
                                            if (stackIds.firstOrNull()?.hashCode()?.and(1) == 0) {
                                                -PILE_TAP_JIGGLE_WOBBLE_DEG
                                            } else {
                                                PILE_TAP_JIGGLE_WOBBLE_DEG
                                            }
                                        jiggleRot.snapTo(wobble)
                                        jiggleScale.animateTo(PILE_TAP_JIGGLE_SCALE, MotionTokens.softEnterSpec())
                                        jiggleRot.animateTo(0f, MotionTokens.softEnterSpec())
                                        jiggleScale.animateTo(1f, MotionTokens.softEnterSpec())
                                    }
                                    PlatformHapticsPolicy.lightImpact()
                                    onExpand()
                                }
                            }
                            return@awaitEachGesture
                        }
                        liveX = animX.value
                        liveY = animY.value
                        dragging = true
                        liveX += slopChange.positionChange().x
                        liveY += slopChange.positionChange().y
                        liveX = pileRubberBandOffset(liveX, cardSizePx)
                        liveY = pileRubberBandOffset(liveY, cardSizePx)
                        velocityTracker.addPosition(slopChange.uptimeMillis, slopChange.position)
                        val completed =
                            drag(slopChange.id) { change ->
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                liveX += change.positionChange().x
                                liveY += change.positionChange().y
                                liveX = pileRubberBandOffset(liveX, cardSizePx)
                                liveY = pileRubberBandOffset(liveY, cardSizePx)
                            }
                        val velocity = velocityTracker.calculateVelocity()
                        val releaseX = liveX
                        val releaseY = liveY
                        dragging = false
                        val action =
                            pileSwipeAction(
                                offsetXPx = releaseX,
                                offsetYPx = releaseY,
                                velocityXPxPerSec = velocity.x,
                                velocityYPxPerSec = velocity.y,
                                sizePx = cardSizePx,
                                canDismiss = stackIds.size > 1,
                                canRecall = dismissedIds.isNotEmpty(),
                                lastExitXPx = lastExitX,
                                lastExitYPx = lastExitY,
                            )
                        if (!completed) {
                            scope.launch {
                                animX.snapTo(releaseX)
                                animY.snapTo(releaseY)
                                launch { animX.animateTo(0f, MotionTokens.softEnterSpec()) }
                                launch { animY.animateTo(0f, MotionTokens.softEnterSpec()) }
                            }
                            return@awaitEachGesture
                        }
                        scope.launch {
                            animX.snapTo(releaseX)
                            animY.snapTo(releaseY)
                            when (action) {
                                PileSwipeAction.Dismiss -> {
                                    val (exitX, exitY) = pileCardExitTargetPx(releaseX, releaseY, cardSizePx)
                                    lastExitX = releaseX
                                    lastExitY = releaseY
                                    coroutineScope {
                                        launch {
                                            animX.animateTo(
                                                exitX,
                                                MotionTokens.softExitSpec(),
                                                initialVelocity = velocity.x,
                                            )
                                        }
                                        launch {
                                            animY.animateTo(
                                                exitY,
                                                MotionTokens.softExitSpec(),
                                                initialVelocity = velocity.y,
                                            )
                                        }
                                    }
                                    val dismissed = stackIds.first()
                                    stackIds = stackIds.drop(1)
                                    dismissedIds = dismissedIds + dismissed
                                    PlatformHapticsPolicy.lightImpact()
                                    animX.snapTo(0f)
                                    animY.snapTo(0f)
                                }
                                PileSwipeAction.Recall -> {
                                    val recalled = dismissedIds.last()
                                    dismissedIds = dismissedIds.dropLast(1)
                                    stackIds = listOf(recalled) + stackIds
                                    val (fromX, fromY) = pileRecallEnterFromPx(releaseX, releaseY, cardSizePx)
                                    animX.snapTo(fromX)
                                    animY.snapTo(fromY)
                                    PlatformHapticsPolicy.lightImpact()
                                    coroutineScope {
                                        launch {
                                            animX.animateTo(
                                                0f,
                                                MotionTokens.softEnterSpec(),
                                                initialVelocity = velocity.x,
                                            )
                                        }
                                        launch {
                                            animY.animateTo(
                                                0f,
                                                MotionTokens.softEnterSpec(),
                                                initialVelocity = velocity.y,
                                            )
                                        }
                                    }
                                }
                                PileSwipeAction.SpringBack -> {
                                    if (reduceMotion) {
                                        animX.snapTo(0f)
                                        animY.snapTo(0f)
                                    } else {
                                        coroutineScope {
                                            launch { animX.animateTo(0f, MotionTokens.softEnterSpec()) }
                                            launch { animY.animateTo(0f, MotionTokens.softEnterSpec()) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
    ) {
        val visibleIds = stackIds.take(PILE_MAX_VISIBLE_LAYERS)
        val visibleLayers = visibleIds.size
        for (reverseLayer in 0 until visibleLayers) {
            val layer = visibleLayers - 1 - reverseLayer
            val photo = photoById[visibleIds[layer]] ?: continue
            val (peekX, peekY) = pilePeekOffsetDp(layer)
            val isTop = layer == 0
            val restTilt = if (reduceMotion) 0f else pileCardTiltDeg(photo.id, layer)
            val scale = if (reduceMotion) 1f else pileLayerScale(layer)
            PhotoCard(
                id = photo.visualId,
                title = if (isTop) photo.title else null,
                subtitle = if (isTop) photo.subtitle else null,
                imageUrl = photo.imageUrl,
                categoryBadge = if (isTop) photo.categoryBadge else null,
                elevation = pileLayerElevationDp(if (isTop && dragging) 0 else layer).dp,
                dimming = if (reduceMotion) 0f else pileLayerDim(layer),
                modifier =
                    Modifier
                        .offset(x = peekX.dp, y = peekY.dp)
                        .width(photoWidth)
                        .height(photoHeight)
                        .zIndex((visibleLayers - layer).toFloat())
                        .graphicsLayer {
                            scaleX = scale * if (isTop) jiggleScale.value else 1f
                            scaleY = scale * if (isTop) jiggleScale.value else 1f
                            alpha = if (reduceMotion) 1f else pileLayerAlpha(layer)
                            if (isTop) {
                                transformOrigin = TransformOrigin(touchAnchorX, touchAnchorY)
                                translationX = if (dragging) liveX else animX.value
                                translationY = if (dragging) liveY else animY.value
                                rotationZ =
                                    if (reduceMotion) {
                                        0f
                                    } else {
                                        pileDragTiltDeg(
                                            if (dragging) liveX else animX.value,
                                            if (dragging) liveY else animY.value,
                                            size.width,
                                        ) + jiggleRot.value
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
}

@Composable
private fun PileFanStrip(
    photos: List<PilePhoto>,
    photoWidth: Dp,
    photoHeight: Dp,
    expanded: Boolean,
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
            val appear = remember(photo.id) { Animatable(if (reduceMotion || expanded) 0f else 1f) }
            LaunchedEffect(photo.id, expanded, reduceMotion) {
                if (reduceMotion) {
                    appear.animateTo(if (expanded) 1f else 0f, tween(durationMillis = 120))
                    return@LaunchedEffect
                }
                if (expanded) {
                    if (appear.value == 0f) {
                        delay(pileFanStaggerMillis(index).toLong())
                    }
                    appear.animateTo(1f, MotionTokens.softEnterSpec())
                } else {
                    delay(pileFanStaggerMillis((photos.lastIndex - index).coerceAtLeast(0)).toLong())
                    appear.animateTo(0f, MotionTokens.softEnterSpec())
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
