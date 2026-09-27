@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

val ClickConversationListRowMinHeight = 72.dp
val ClickConversationAvatarSize = 48.dp
private val ClickConversationPressedShape = RoundedCornerShape(18.dp)
private const val QUICK_TAP_FEEDBACK_HOLD_MS = 90L
private const val PRESS_REVEAL_MILLIS = 170
private const val PRESS_FADE_MILLIS = 220
private const val PRESS_WASH_ALPHA = 0.075f

/**
 * Conversation-specific list row.
 *
 * Android keeps the Material bounded ripple. Without ripple, a lightweight radial wash starts at the
 * actual finger position and expands to the row bounds, instead of flashing the whole touch surface
 * at once. Navigation still yields one frame so very quick taps visibly acknowledge their source.
 */
@Composable
fun ClickConversationListRow(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
    dividerStartIndent: Dp = ClickPlatformListDividerIndent,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val useRipple = LocalPlatformStyle.current.useRipple
    val reveal = remember { Animatable(0f) }
    val washAlpha = remember { Animatable(0f) }
    val pressOrigin = remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    val washColor = MaterialTheme.colorScheme.onSurface

    LaunchedEffect(interactionSource, useRipple) {
        var clearJob: Job? = null
        var revealJob: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    if (!useRipple) {
                        clearJob?.cancel()
                        revealJob?.cancel()
                        pressOrigin.value = interaction.pressPosition
                        washAlpha.snapTo(1f)
                        reveal.snapTo(0f)
                        revealJob =
                            launch {
                                reveal.animateTo(
                                    targetValue = 1f,
                                    animationSpec =
                                        tween(
                                            durationMillis = PRESS_REVEAL_MILLIS,
                                            easing = FastOutSlowInEasing,
                                        ),
                                )
                            }
                    }
                }
                is PressInteraction.Release,
                is PressInteraction.Cancel,
                -> {
                    if (!useRipple) {
                        clearJob?.cancel()
                        clearJob =
                            launch {
                                delay(QUICK_TAP_FEEDBACK_HOLD_MS)
                                washAlpha.animateTo(
                                    targetValue = 0f,
                                    animationSpec =
                                        tween(
                                            durationMillis = PRESS_FADE_MILLIS,
                                            easing = LinearOutSlowInEasing,
                                        ),
                                )
                                reveal.snapTo(0f)
                            }
                    }
                }
            }
        }
    }

    val dispatchClick: () -> Unit = {
        if (useRipple) {
            onClick()
        } else {
            scope.launch {
                withFrameNanos { }
                onClick()
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ClickConversationListRowMinHeight)
                    .clip(ClickConversationPressedShape)
                    .drawWithContent {
                        drawContent()
                        if (!useRipple && washAlpha.value > 0.001f && reveal.value > 0.001f) {
                            val raw = pressOrigin.value
                            val center =
                                Offset(
                                    x = raw.x.coerceIn(0f, size.width),
                                    y = raw.y.coerceIn(0f, size.height),
                                )
                            val dx = maxOf(center.x, size.width - center.x)
                            val dy = maxOf(center.y, size.height - center.y)
                            val maxRadius = sqrt(dx * dx + dy * dy)
                            drawCircle(
                                color = washColor.copy(alpha = PRESS_WASH_ALPHA * washAlpha.value),
                                radius = maxRadius * reveal.value,
                                center = center,
                            )
                        }
                    }.combinedClickable(
                        interactionSource = interactionSource,
                        indication = if (useRipple) ripple(bounded = true) else null,
                        onClick = dispatchClick,
                        onLongClick = onLongPress,
                    ).defaultMinSize(minHeight = ClickConversationListRowMinHeight)
                    .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Box(
                    modifier =
                        Modifier.defaultMinSize(
                            minWidth = ClickConversationAvatarSize,
                            minHeight = ClickConversationAvatarSize,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    leading()
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f), content = content)
            if (trailing != null) {
                Spacer(modifier = Modifier.width(ClickScreenSpacing.Compact))
                trailing()
            }
        }
        if (showDivider) {
            ClickInsetDivider(startIndent = dividerStartIndent)
        }
    }
}
