package compose.project.click.click.ui.components.native

import androidx.compose.runtime.Composable
import compose.project.click.click.calls.CallOverlayState

@Composable
expect fun NativeCallPreviewHost(
    overlayState: CallOverlayState,
    callerName: String,
    isVideo: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onDismissEnded: () -> Unit,
)
