@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.ui.theme.ClickAccent // pragma: allowlist secret
import compose.project.click.click.ui.theme.pileSlotForCluster // pragma: allowlist secret

data class PilePhoto(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    val content: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
)

/**
 * Overlapping polaroid stack. Tap the cluster to fan into a horizontal strip;
 * tap outside (parent) or back to collapse. Individual photos keep their own click.
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
    index: Int = 0,
    totalClusters: Int = 1,
    zPriority: Float = 0f,
    photoWidth: Dp = 148.dp,
    photoHeight: Dp = 188.dp,
) {
    if (photos.isEmpty()) return
    val slot = pileSlotForCluster(clusterId, index, totalClusters)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val x = (maxWidth * slot.xFrac) - photoWidth / 2
        val y = (maxHeight * slot.yFrac) - photoHeight / 2
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(x.roundToPx(), y.roundToPx()) }
                    .zIndex(zPriority + if (expanded) 50f else 0f)
                    .semantics { contentDescription = label },
        ) {
            if (expanded) {
                Row(
                    modifier =
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    photos.forEachIndexed { i, photo ->
                        val delayFrac = i / photos.size.coerceAtLeast(1).toFloat()
                        val appear by animateFloatAsState(
                            targetValue = 1f,
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            label = "pile_fan_$i",
                        )
                        PhotoCard(
                            id = photo.id,
                            title = photo.title,
                            subtitle = photo.subtitle,
                            imageUrl = photo.imageUrl,
                            modifier =
                                Modifier
                                    .width(photoWidth)
                                    .height(photoHeight)
                                    .graphicsLayer {
                                        scaleX = 0.92f + 0.08f * appear
                                        scaleY = 0.92f + 0.08f * appear
                                        alpha = appear
                                        translationY = (1f - appear) * (8f + delayFrac * 12f)
                                    },
                            onClick = photo.onClick,
                            onLongClick = photo.onLongClick,
                            content = photo.content,
                        )
                    }
                }
            } else {
                val stacked = photos.take(4)
                Box(
                    modifier =
                        Modifier
                            .width(photoWidth + 16.dp)
                            .height(photoHeight + 16.dp)
                            .pointerInput(clusterId) {
                                detectTapGestures(onTap = { onExpand() })
                            },
                ) {
                    stacked.reversed().forEachIndexed { reverseIndex, photo ->
                        val i = stacked.lastIndex - reverseIndex
                        val rot = slot.rotationDeg + (i - 1) * 4.5f
                        PhotoCard(
                            id = photo.id,
                            title = if (i == stacked.lastIndex) photo.title else null,
                            subtitle = if (i == stacked.lastIndex) photo.subtitle else null,
                            imageUrl = photo.imageUrl,
                            modifier =
                                Modifier
                                    .align(Alignment.Center)
                                    .offset(x = (i * 4).dp, y = (i * 3).dp)
                                    .width(photoWidth)
                                    .height(photoHeight)
                                    .graphicsLayer { rotationZ = rot }
                                    .zIndex(i.toFloat()),
                            onClick = { onExpand() },
                            content = if (i == stacked.lastIndex) photo.content else null,
                        )
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 8.dp, y = (-10).dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ClickAccent.colorForStableId(clusterId).copy(alpha = 0.92f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .zIndex(20f),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = androidx.compose.ui.graphics.Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun PileBoardScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Color.Black
                        .copy(alpha = 0.28f),
                ).pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }.zIndex(40f),
    )
}
