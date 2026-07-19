package compose.project.click.click.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.clickBorderColor

/**
 * Circular bordered glass FAB used by map Drop beacon / zoom and inbox Create click.
 * Clips before [clickable] so the hit target matches the visible circle.
 */
@Composable
fun ClickCircularIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 56.dp,
    iconSize: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    showBorder: Boolean = false,
    glassStrength: Float? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressOffset by animateDpAsState(
        targetValue = if (isPressed) LocalPlatformStyle.current.pressOffset else 0.dp,
        animationSpec = spring(),
        label = "circular_icon_press_offset",
    )
    val density = LocalDensity.current
    val buttonModifier = modifier
        .size(size)
        .graphicsLayer {
            translationY = with(density) { pressOffset.toPx() }
            alpha = if (!enabled) 0.38f else if (isPressed) 0.92f else 1f
        }
        .clip(CircleShape)
        .then(
            if (showBorder) Modifier.border(2.dp, clickBorderColor(), CircleShape)
            else Modifier
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )

    val iconContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        }
    }

    if (glassStrength != null) {
        LiquidGlassPill(
            modifier = buttonModifier,
            cornerRadiusDp = (size.value / 2f).toInt().coerceAtLeast(22),
            backgroundStrength = glassStrength,
            contentPaddingHorizontal = 0.dp,
            contentPaddingVertical = 0.dp,
            content = iconContent,
        )
    } else {
        Box(modifier = buttonModifier, contentAlignment = Alignment.Center) {
            iconContent()
        }
    }
}

@Composable
fun ClickCircularGlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    glassStrength: Float = if (LocalPlatformStyle.current.isIOS) 0.64f else 0.4f,
) {
    ClickCircularIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        size = size,
        glassStrength = glassStrength,
    )
}
