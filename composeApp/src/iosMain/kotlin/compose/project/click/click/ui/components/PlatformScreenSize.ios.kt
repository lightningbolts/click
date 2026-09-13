package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIScreen

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun rememberPlatformScreenSizeDp(): DpSize =
    UIScreen.mainScreen.bounds.useContents {
        DpSize(size.width.dp, size.height.dp)
    }
