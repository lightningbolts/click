package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun QRScanner(
    modifier: Modifier = Modifier,
    onResult: (String) -> Unit
)
