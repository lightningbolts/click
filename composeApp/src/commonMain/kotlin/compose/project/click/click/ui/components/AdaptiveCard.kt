package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.* // pragma: allowlist secret

@Composable
fun AdaptiveCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val radius = getAdaptiveCornerRadius()
    val shape = RoundedCornerShape(radius)
    val cardModifier = modifier.border(
        width = clickBorderWidth(),
        color = clickBorderColor(),
        shape = shape
    )

    if (onClick != null) {
        Surface(
            modifier = cardModifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp,
            onClick = onClick,
            content = {
                Column(modifier = Modifier.padding(getAdaptivePadding()), content = content)
            }
        )
    } else {
        Surface(
            modifier = cardModifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp,
            content = {
                Column(modifier = Modifier.padding(getAdaptivePadding()), content = content)
            }
        )
    }
}

@Composable
fun AdaptiveSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val radius = getAdaptiveCornerRadius()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = clickBorderWidth(),
                color = clickBorderColor(),
                shape = RoundedCornerShape(bottomStart = radius, bottomEnd = radius),
            ),
        shape = RoundedCornerShape(bottomStart = radius, bottomEnd = radius),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(content = content)
    }
}

@Composable
fun AdaptiveBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        content = content
    )
}

enum class ClickButtonVariant {
    Primary,
    Secondary,
    Destructive,
}

/**
 * Canonical action button. Screens must use this instead of one-off [Button] styling.
 * [AdaptiveButton] remains as the primary-variant alias.
 */
@Composable
fun ClickButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ClickButtonVariant = ClickButtonVariant.Primary,
    content: @Composable RowScope.() -> Unit,
) {
    val style = LocalPlatformStyle.current
    val colors = when (variant) {
        ClickButtonVariant.Primary -> ButtonDefaults.buttonColors(
            containerColor = PrimaryBlue,
            contentColor = Color.White,
            disabledContainerColor = SurfaceContainerHigh,
            disabledContentColor = OnSurfaceVariant,
        )
        ClickButtonVariant.Secondary -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = SurfaceContainerHigh,
            disabledContentColor = OnSurfaceVariant,
        )
        ClickButtonVariant.Destructive -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            disabledContainerColor = SurfaceContainerHigh,
            disabledContentColor = OnSurfaceVariant,
        )
    }
    val border = when (variant) {
        ClickButtonVariant.Primary -> null
        ClickButtonVariant.Secondary -> clickBorderStroke()
        ClickButtonVariant.Destructive -> null
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
        ),
        border = border,
        shape = RoundedCornerShape(style.buttonCornerRadius),
        content = content,
    )
}

@Composable
fun AdaptiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ClickButtonVariant = ClickButtonVariant.Primary,
    content: @Composable RowScope.() -> Unit,
) {
    ClickButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        variant = variant,
        content = content,
    )
}

@Composable
fun getAdaptiveCornerRadius(): Dp {
    return LocalPlatformStyle.current.cardCornerRadius
}

@Composable
fun getAdaptivePadding(): Dp {
    return 16.dp
}
