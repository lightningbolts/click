package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Full-bleed glass nudge overlay — delegates to [UnifiedToastOverlay].
 */
@Composable
fun GlassmorphicOverlay(
    visible: Boolean,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnifiedToastOverlay(
        visible = visible,
        message = message,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}
