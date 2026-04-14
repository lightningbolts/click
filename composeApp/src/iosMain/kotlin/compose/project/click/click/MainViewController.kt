@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package compose.project.click.click

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.ComposeUIViewController
import compose.project.click.click.ui.utils.AppSystemSettings
import kotlin.native.Platform

fun MainViewController() = ComposeUIViewController {
    AppSystemSettings.isDebugMode = Platform.isDebugBinary
    CompositionLocalProvider(
        LocalHapticFeedback provides remember { IosUIKitHapticFeedback() },
    ) {
        App()
    }
}