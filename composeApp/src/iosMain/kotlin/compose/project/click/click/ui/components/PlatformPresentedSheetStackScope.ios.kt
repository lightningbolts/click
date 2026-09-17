@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi

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
