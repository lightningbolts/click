@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package compose.project.click.click

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.ComposeUIViewController
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.ui.utils.AppSystemSettings
import io.github.jan.supabase.auth.handleDeeplinks
import kotlin.native.Platform
import platform.Foundation.NSURL

/**
 * OAuth / magic-link return path for iOS. Call from Swift when the app opens `click://login…`
 * so supabase-kt can finish the PKCE exchange.
 */
fun handleSupabaseAuthDeepLink(url: NSURL) {
    SupabaseConfig.client.handleDeeplinks(url)
}

fun MainViewController() = ComposeUIViewController {
    AppSystemSettings.isDebugMode = Platform.isDebugBinary
    CompositionLocalProvider(
        LocalHapticFeedback provides remember { IosUIKitHapticFeedback() },
    ) {
        App()
    }
}