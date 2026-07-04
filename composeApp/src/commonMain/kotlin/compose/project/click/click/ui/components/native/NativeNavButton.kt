package compose.project.click.click.ui.components.native

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class NavButtonStyle {
    /** Standard 48.dp circular icon button. */
    Icon,
    /** FAB-sized prominent circle (e.g. send, groups FAB). */
    Prominent,
}

@Composable
expect fun NativeNavButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    style: NavButtonStyle = NavButtonStyle.Icon,
    size: Dp? = null,
)

@Composable
fun NativeBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Back",
    enabled: Boolean = true,
    tint: Color? = null,
    size: Dp? = null,
) {
    NativeNavButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        tint = tint,
        style = NavButtonStyle.Icon,
        size = size,
    )
}
