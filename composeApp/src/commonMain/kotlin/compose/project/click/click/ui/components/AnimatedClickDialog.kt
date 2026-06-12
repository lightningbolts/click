package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Confirmation / form dialog — delegates to [UnifiedPopupFormDialog].
 */
@Composable
fun AnimatedClickDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = "Cancel",
    body: @Composable () -> Unit,
) {
    UnifiedPopupFormDialog(
        visible = visible,
        onDismissRequest = onDismissRequest,
        title = title,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        modifier = modifier,
        dismissLabel = dismissLabel,
        body = body,
    )
}
