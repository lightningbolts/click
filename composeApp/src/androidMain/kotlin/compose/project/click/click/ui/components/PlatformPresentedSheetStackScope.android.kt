package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformPresentedSheetStackScope(content: @Composable () -> Unit) {
    content()
}
