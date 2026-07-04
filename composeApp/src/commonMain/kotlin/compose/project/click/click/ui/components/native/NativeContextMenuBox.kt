package compose.project.click.click.ui.components.native

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

data class NativeContextMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
)

@Composable
expect fun NativeContextMenuBox(
    items: List<NativeContextMenuItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
)

@Composable
expect fun NativeContextMenuIconButton(
    icon: ImageVector,
    contentDescription: String,
    items: List<NativeContextMenuItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    size: Dp? = null,
    onIconClick: (() -> Unit)? = null,
)

@Composable
expect fun NativeContextMenuChip(
    label: String,
    items: List<NativeContextMenuItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
)
