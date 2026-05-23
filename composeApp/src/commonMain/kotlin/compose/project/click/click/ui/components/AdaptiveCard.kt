package compose.project.click.click.ui.components

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
import compose.project.click.click.ui.theme.*

@Composable
fun AdaptiveCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val style = LocalPlatformStyle.current
    val radius = getAdaptiveCornerRadius()
    val shape = RoundedCornerShape(radius)
    val borderWidth = style.cardBorderWidth
    val surfaceAlpha = if (style.isIOS) 0.85f else 0.8f

    val cardModifier = modifier.border(
        width = borderWidth,
        color = PrimaryBlue.copy(alpha = if (style.isIOS) 0.35f else 0.5f),
        shape = shape
    )

    if (onClick != null) {
        Surface(
            modifier = cardModifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha),
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
            color = MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha),
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
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = radius, bottomEnd = radius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
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
        modifier = modifier.background(Color.Transparent),
        content = content
    )
}

@Composable
fun AdaptiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val style = LocalPlatformStyle.current

    if (style.isIOS) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryBlue.copy(alpha = 0.15f),
                contentColor = PrimaryBlue,
                disabledContainerColor = Color.Gray.copy(alpha = 0.08f),
                disabledContentColor = Color.Gray
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
            ),
            shape = RoundedCornerShape(style.buttonCornerRadius),
            content = content
        )
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledContainerColor = Color.Gray.copy(alpha = 0.1f),
                disabledContentColor = Color.Gray
            ),
            border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(style.buttonCornerRadius),
            content = content
        )
    }
}

@Composable
fun getAdaptiveCornerRadius(): Dp {
    return LocalPlatformStyle.current.cardCornerRadius
}

@Composable
fun getAdaptivePadding(): Dp {
    return 16.dp
}
