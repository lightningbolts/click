@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformPresentedSheetStackScope(content: @Composable () -> Unit) {
    content()
}
