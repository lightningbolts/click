package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize

@Composable
internal actual fun rememberPlatformScreenSizeDp(): DpSize {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return with(density) { DpSize(size.width.toDp(), size.height.toDp()) }
}
