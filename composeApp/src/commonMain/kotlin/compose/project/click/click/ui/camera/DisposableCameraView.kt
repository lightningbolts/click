package compose.project.click.click.ui.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Locket-style in-app camera for Disposable Roll captures.
 * Frames stay in memory ([ByteArray]) — never written to the OS photo library.
 */
@Composable
expect fun DisposableCameraView(
    onPhotoConfirmed: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
