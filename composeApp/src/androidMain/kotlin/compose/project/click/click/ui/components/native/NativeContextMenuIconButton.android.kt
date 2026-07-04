package compose.project.click.click.ui.components.native

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

@Composable
actual fun NativeContextMenuIconButton(
    icon: ImageVector,
    contentDescription: String,
    items: List<NativeContextMenuItem>,
    modifier: Modifier,
    enabled: Boolean,
    tint: Color?,
    size: Dp?,
    onIconClick: (() -> Unit)?,
) {
    NativeContextMenuBox(
        items = items,
        modifier = modifier,
        enabled = enabled,
    ) {
        NativeNavButton(
            icon = icon,
            contentDescription = contentDescription,
            onClick = onIconClick ?: {},
            enabled = enabled,
            tint = tint,
            size = size,
        )
    }
}
