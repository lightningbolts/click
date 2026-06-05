package compose.project.click.click

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import compose.project.click.click.ui.screens.AppClipHandshakeScreen
import compose.project.click.click.ui.theme.PlatformThemeProvider

/**
 * Lightweight Compose entry point for the iOS App Clip target.
 * Excludes full [App] navigation, auth, and heavy feature modules.
 */
fun AppClipMainViewController(invocationUrl: String?) = ComposeUIViewController(
    configure = {
        onFocusBehavior = OnFocusBehavior.DoNothing
    },
) {
    PlatformThemeProvider(isDarkMode = true) {
        AppClipHandshakeScreen(invocationUrl = invocationUrl)
    }
}
