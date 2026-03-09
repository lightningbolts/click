package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class QrScannerDetection(
    val normalizedCenterX: Float,
    val normalizedCenterY: Float,
    val normalizedSize: Float
)

@Composable
expect fun QRScanner(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    onDetectionChanged: (QrScannerDetection?) -> Unit = {},
    onResult: (String) -> Unit
)
