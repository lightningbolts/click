package compose.project.click.click.ui.chat

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.components.ClickCircularIconButton

/** Shared horizontal inset for chat header row and composer strip (outer edges align). */
internal val ChatChromeHorizontalPadding: Dp = 16.dp

/**
 * Circular header action for chat / hub threads.
 *
 * Prefer [showBorder]=true only for the primary back control. Trailing actions (edit / call / ⋮)
 * stay borderless so a row of 40dp rings does not crowd the title. Composer +/send keep their
 * own borders in [ConnectionChatMessageComposer] / hub input.
 */
@Composable
internal fun ChatHeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    showBorder: Boolean = false,
) {
    ClickCircularIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        tint = tint,
        size = size,
        iconSize = iconSize,
        showBorder = showBorder,
    )
}

/**
 * Opaque Functional Clarity plate for chat chrome (no blur).
 */
@Composable
internal fun ChatLiquidGlassPlate(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.surface,
    blurRadius: Dp = 18.dp,
    testTag: String,
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredBlur = blurRadius
    Box(
        modifier = modifier
            .graphicsLayer { clip = true }
            .background(tint)
            .testTag(testTag),
    )
}

/**
 * Solid underlay for composer chrome — no gradient fades.
 */
@Composable
internal fun ChatComposerChromeFadeUnderlay(
    modifier: Modifier = Modifier,
    testTag: String = ChatGlassComposerPlateTestTag,
) {
    val bg = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .testTag(testTag)
            .background(bg),
    )
}

@Composable
internal fun Modifier.chatSpringPressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val offset by animateDpAsState(
        targetValue = if (pressed) LocalPlatformStyle.current.pressOffset else 0.dp,
        label = "chat_icon_press_offset",
    )
    val density = LocalDensity.current
    return this.graphicsLayer {
        translationY = with(density) { offset.toPx() }
        alpha = if (pressed) 0.92f else 1f
    }
}

/** Text-field container colors — opaque bordered Functional Clarity fields. */
@Composable
internal fun rememberChatComposerFieldColors(): TextFieldColors {
    val fieldFill = MaterialTheme.colorScheme.surface
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PrimaryBlue,
        unfocusedBorderColor = compose.project.click.click.ui.theme.clickBorderColor(),
        focusedContainerColor = fieldFill,
        unfocusedContainerColor = fieldFill,
    )
}

/** Spring bounce + null indication; pair with glass border tweaks at call sites for tactile feedback. */
@Composable
fun Modifier.bouncingClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return bouncingClickable(interactionSource, enabled, onClick)
}

@Composable
fun Modifier.bouncingClickable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier =
    this.chatSpringPressScale(interactionSource).clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
