package compose.project.click.click.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    // Use Material surface everywhere for consistency and dark mode support
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(getAdaptiveCornerRadius()),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        onClick = onClick ?: {}
    ) {
        Column(
            modifier = Modifier.padding(getAdaptivePadding()),
            content = content
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
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
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
    // Derive from theme for dark mode
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary
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
