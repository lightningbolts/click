package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties

/** @see LocalUnifiedPopupAnimatedDismiss */
val LocalGlassAlertAnimatedDismiss = staticCompositionLocalOf<() -> Unit> {
    error("LocalGlassAlertAnimatedDismiss used outside GlassAlertDialog")
}

/**
 * Centered OLED alert — delegates to [UnifiedPopupAlert].
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    showActionRow: Boolean = true,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    UnifiedPopupAlert(
        onDismissRequest = onDismissRequest,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        icon = icon,
        showActionRow = showActionRow,
        properties = properties,
    )
}

/** TextButton tuned for OLED glass dialogs. */
@Composable
fun GlassDialogTextButton(
    label: String,
    onClick: () -> Unit,
    contentColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
) {
    UnifiedPopupTextButton(label = label, onClick = onClick, contentColor = contentColor)
}
