package compose.project.click.click.ui.components.native

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy

@Composable
actual fun NativeNavButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    tint: Color?,
    style: NavButtonStyle,
    size: Dp?,
) {
    val iconTint = tint ?: MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.85f else 0.4f)
    val buttonSize = size ?: when (style) {
        NavButtonStyle.Icon -> 48.dp
        NavButtonStyle.Prominent -> 56.dp
    }
    val triggerClick = {
        if (enabled) {
            PlatformHapticsPolicy.lightImpact()
            onClick()
        }
    }
    when (style) {
        NavButtonStyle.Prominent -> {
            FloatingActionButton(
                onClick = triggerClick,
                modifier = modifier.size(buttonSize),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = iconTint,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size((buttonSize.value * 0.45f).dp),
                    tint = iconTint,
                )
            }
        }
        NavButtonStyle.Icon -> {
            IconButton(
                onClick = triggerClick,
                enabled = enabled,
                modifier = modifier.size(buttonSize),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = iconTint,
                )
            }
        }
    }
}
