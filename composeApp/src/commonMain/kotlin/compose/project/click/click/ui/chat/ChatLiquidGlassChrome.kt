package compose.project.click.click.ui.chat

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Translucent plate + blur for chat chrome (samples content behind on supported
 * platforms; older targets still get the soft tint).
 */
@Composable
internal fun ChatLiquidGlassPlate(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
    blurRadius: Dp = 18.dp,
    testTag: String,
) {
    Box(
        modifier = modifier
            .background(tint)
            .blur(blurRadius)
            .testTag(testTag),
    )
}

@Composable
internal fun Modifier.chatSpringPressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "chat_icon_press_scale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
