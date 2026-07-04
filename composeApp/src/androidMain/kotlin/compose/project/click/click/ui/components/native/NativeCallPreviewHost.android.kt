package compose.project.click.click.ui.components.native

import androidx.compose.runtime.Composable
import compose.project.click.click.calls.CallOverlayState
import compose.project.click.click.calls.CallPreviewOverlay

@Composable
actual fun NativeCallPreviewHost(
    overlayState: CallOverlayState,
    callerName: String,
    isVideo: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onDismissEnded: () -> Unit,
) {
    CallPreviewOverlay(
        overlayState = overlayState,
        currentUserId = null,
        onAccept = onAccept,
        onDecline = onDecline,
        onCancel = onCancel,
        onDismissEnded = onDismissEnded,
    )
}
