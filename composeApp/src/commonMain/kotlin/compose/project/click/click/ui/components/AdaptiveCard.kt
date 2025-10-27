package compose.project.click.click.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
    val isIOS = getPlatform().name.contains("iOS", ignoreCase = true)

    if (isIOS) {
        // iOS: Liquid Glass Style
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.7f),
            shadowElevation = 0.dp,
            onClick = onClick ?: {}
        ) {
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.3f))
                    .border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    content = content
                )
            }
        }
    } else {
        // Android: Material You
        ElevatedCard(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = SurfaceLight
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = 2.dp
            ),
            onClick = onClick ?: {}
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
fun AdaptiveSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isIOS = getPlatform().name.contains("iOS", ignoreCase = true)

    if (isIOS) {
        // iOS: Frosted glass header
        Surface(
            modifier = modifier,
            color = Color.White.copy(alpha = 0.8f),
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.4f))
                    .border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
            ) {
                Column(content = content)
            }
        }
    } else {
        // Android: Material surface
        Surface(
            modifier = modifier,
            color = GlassLight.copy(alpha = 0.95f),
            shadowElevation = 2.dp,
            tonalElevation = 1.dp
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun AdaptiveBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isIOS = getPlatform().name.contains("iOS", ignoreCase = true)

    Box(
        modifier = modifier.background(
            if (isIOS) Color(0xFFF8F9FA) else BackgroundLight
        ),
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
    val isIOS = getPlatform().name.contains("iOS", ignoreCase = true)

    if (isIOS) {
        // iOS: Filled button with rounded corners
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryBlue,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            ),
            content = content
        )
    } else {
        // Android: Material You button
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = SoftBlue,
                contentColor = PrimaryBlue
            ),
            shape = RoundedCornerShape(20.dp),
            content = content
        )
    }
}

@Composable
fun getAdaptiveCornerRadius(): androidx.compose.ui.unit.Dp {
    val isIOS = getPlatform().name.contains("iOS", ignoreCase = true)
    return if (isIOS) 20.dp else 16.dp
}

@Composable
fun getAdaptivePadding(): androidx.compose.ui.unit.Dp {
    val isIOS = getPlatform().name.contains("iOS", ignoreCase = true)
    return if (isIOS) 16.dp else 20.dp
}

