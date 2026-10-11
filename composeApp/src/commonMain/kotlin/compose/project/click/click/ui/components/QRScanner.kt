package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class QrScannerDetection(
    val normalizedCenterX: Float,
    val normalizedCenterY: Float,
    val normalizedSize: Float,
)

@Composable
@Suppress("ktlint:standard:function-naming")
expect fun QRScanner(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    onDetectionChanged: (QrScannerDetection?) -> Unit = {},
    /** Shown instead of the preview when the camera permission is denied. */
    deniedMessage: String = "Camera permission required",
    /**
     * When false (default) a value is delivered once until [isActive] toggles. The door scanner
     * passes true and de-duplicates itself (the same pass may be scanned again after 3 s).
     */
    deliverRepeats: Boolean = false,
    onResult: (String) -> Unit,
)
