@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.platform.rememberReduceMotionEnabled // pragma: allowlist secret
import compose.project.click.click.ui.theme.MotionTokens // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret

/** Inbox-style row height: avatar + two text lines. */
val ClickPlatformListRowHeight = 72.dp

/** Indent so the divider aligns with the text column after a 44dp avatar + 16dp leading + 12dp gap. */
val ClickPlatformListDividerIndent = 72.dp

/** Settings rows: 32dp icon well + 16dp leading + 8dp gap. */
val ClickSettingsDividerIndent = 56.dp

/**
 * Flat, full-width list divider. Use under inbox / search / settings rows instead of card strokes.
 */
@Composable
fun ClickInsetDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = ClickPlatformListDividerIndent,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
}

/** Unread affordance: 8dp brand dot, no numeric badge. */
@Composable
fun ClickUnreadDot(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(PrimaryBlue),
    )
}

/**
 * Press scale for tappable cards and list rows. Pair with a non-null [Indication]
 * (`ripple` or the row wash) — never `indication = null`.
 */
@Composable
fun Modifier.platformPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = MotionTokens.PressScale.CardPressedScale,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberReduceMotionEnabled()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) pressedScale else 1f,
        animationSpec = MotionTokens.pressScaleSpec(),
        label = "platform_press_scale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Skeleton for unresolved inbox / hub rows — same alpha pulse as chat subtitle placeholders. */
@Composable
fun ClickListRowShimmer(modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotionEnabled()
    val transition = rememberInfiniteTransition(label = "click_list_row_shimmer")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.62f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "click_list_row_shimmer_alpha",
    )
    val alpha = if (reduceMotion) 0.45f else pulseAlpha
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(ClickPlatformListRowHeight)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.42f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.72f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
            )
        }
    }
}
