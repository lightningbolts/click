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

    val cardModifier = modifier.border(
        width = borderWidth,
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
                width = LocalPlatformStyle.current.cardBorderWidth,
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

@Composable
fun AdaptiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val style = LocalPlatformStyle.current
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryBlue,
            contentColor = Color.White,
            disabledContainerColor = SurfaceContainerHigh,
            disabledContentColor = OnSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
        ),
        border = BorderStroke(style.cardBorderWidth, clickBorderColor()),
        shape = RoundedCornerShape(style.buttonCornerRadius),
        content = content
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
