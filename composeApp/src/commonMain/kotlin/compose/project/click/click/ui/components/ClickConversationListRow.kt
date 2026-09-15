@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val ClickConversationListRowMinHeight = 72.dp
val ClickConversationAvatarSize = 48.dp
private val ClickConversationPressedShape = RoundedCornerShape(18.dp)
private const val QuickTapFeedbackHoldMs = 90L

/**
 * Conversation-specific list row.
 *
 * Chat inbox rows intentionally have more vertical breathing room than generic settings/search
 * rows. iOS does not use a ripple, so a short-lived pressed wash is latched through very fast taps
 * instead of depending on a press/release pair surviving long enough to be drawn in one frame.
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
    var pressedVisible by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        var clearJob: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    clearJob?.cancel()
                    pressedVisible = true
                }
                is PressInteraction.Release,
                is PressInteraction.Cancel,
                -> {
                    clearJob?.cancel()
                    clearJob =
                        launch {
                            delay(QuickTapFeedbackHoldMs)
                            pressedVisible = false
                        }
                }
            }
        }
    }

    val pressedWash =
        if (pressedVisible) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.075f)
        } else {
            Color.Transparent
        }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ClickConversationListRowMinHeight)
                    .clip(ClickConversationPressedShape)
                    .background(pressedWash)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = if (LocalPlatformStyle.current.useRipple) ripple(bounded = true) else null,
                        onClick = onClick,
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
