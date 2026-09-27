package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize

/** Physical screen size in dp. Used to edge-bleed full-screen popups. */
@Composable
internal expect fun rememberPlatformScreenSizeDp(): DpSize
