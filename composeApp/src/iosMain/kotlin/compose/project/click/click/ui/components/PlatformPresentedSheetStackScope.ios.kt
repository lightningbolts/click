@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.uikit.LocalUIViewController

@Composable
actual fun PlatformPresentedSheetStackScope(content: @Composable () -> Unit) {
    val compositionHost = LocalUIViewController.current
    var presenter = compositionHost
    while (true) {
        val next = presenter.presentedViewController ?: break
        presenter = next
    }

    CompositionLocalProvider(LocalUIViewController provides presenter) {
        content()
    }
}
