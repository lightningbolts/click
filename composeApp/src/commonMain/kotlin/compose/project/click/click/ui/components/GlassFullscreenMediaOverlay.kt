package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Full-screen media lightbox — delegates to [UnifiedPopupOverlay].
 */
@Composable
fun GlassFullscreenMediaOverlay(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrimAlpha: Float = GlassSheetTokens.ScrimBaseAlpha,
    content: @Composable () -> Unit,
) {
    UnifiedPopupOverlay(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        scrimAlpha = scrimAlpha,
        content = content,
    )
}
