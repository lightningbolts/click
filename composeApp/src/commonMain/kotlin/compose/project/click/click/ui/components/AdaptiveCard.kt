package compose.project.click.click.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.project.click.click.getPlatform
import compose.project.click.click.ui.theme.*

@Composable
fun AdaptiveCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // Glassmorphism Card with Glowing Border
    val cardModifier = modifier
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(PrimaryBlue.copy(alpha = 0.5f), Color.Transparent)
            ),
            shape = RoundedCornerShape(getAdaptiveCornerRadius())
        )

    if (onClick != null) {
        Surface(
            modifier = cardModifier,
            shape = RoundedCornerShape(getAdaptiveCornerRadius()),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            shadowElevation = 0.dp,
            onClick = onClick,
            content = {
                Column(
                    modifier = Modifier.padding(getAdaptivePadding()),
                    content = content
                )
            }
        )
    } else {
        Surface(
            modifier = cardModifier,
            shape = RoundedCornerShape(getAdaptiveCornerRadius()),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            shadowElevation = 0.dp,
            content = {
                Column(
                    modifier = Modifier.padding(getAdaptivePadding()),
                    content = content
                )
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
        modifier = modifier.background(Color.Transparent), // Background handled by App.kt
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
    // Glassmorphic Button
    Button(
        onClick = onClick,
        modifier = modifier.border(
            width = 1.dp,
            color = PrimaryBlue.copy(alpha = 0.5f),
            shape = RoundedCornerShape(20.dp)
        ),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = Color.Gray.copy(alpha = 0.1f),
            disabledContentColor = Color.Gray
        ),
        shape = RoundedCornerShape(20.dp),
        content = content
    )
}

@Composable
fun getAdaptiveCornerRadius(): androidx.compose.ui.unit.Dp {
    return 16.dp
}

@Composable
fun getAdaptivePadding(): androidx.compose.ui.unit.Dp {
    return 16.dp
}
