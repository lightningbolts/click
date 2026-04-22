@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package compose.project.click.click

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.ComposeUIViewController
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.ui.utils.AppSystemSettings
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.datetime.Clock
import kotlin.native.Platform
import platform.Foundation.NSURL

/**
 * OAuth / magic-link return path for iOS. Call from Swift when the app opens `click://login…`
 * so supabase-kt can finish the PKCE exchange.
 */
private var lastHandledAuthUrl: String? = null
private var lastHandledAuthAtMs: Long = 0L

fun handleSupabaseAuthDeepLink(url: NSURL) {
    val raw = url.absoluteString?.toString()?.trim().orEmpty()
    val now = Clock.System.now().toEpochMilliseconds()
    if (raw.isNotEmpty() && raw == lastHandledAuthUrl && now - lastHandledAuthAtMs < 2_000L) {
        return
    }
    lastHandledAuthUrl = raw
    lastHandledAuthAtMs = now

    runCatching { SupabaseConfig.client.handleDeeplinks(url) }
        .onFailure { e ->
            println("MainViewController: OAuth deep-link handling failed: ${e.message}")
        }
}

fun MainViewController() = ComposeUIViewController {
    AppSystemSettings.isDebugMode = Platform.isDebugBinary
    CompositionLocalProvider(
        LocalHapticFeedback provides remember { IosUIKitHapticFeedback() },
    ) {
        App()
    }
}