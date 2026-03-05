package compose.project.click.click.ui.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import platform.Foundation.NSNotificationCenter

/**
 * iOS actual: bridges a native edge-swipe gesture notification into compose back handling.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    DisposableEffect(enabled, onBack) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = IOS_BACK_SWIPE_NOTIFICATION,
            `object` = null,
            queue = null
        ) {
            if (enabled) {
                onBack()
            }
        }

        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }
}

private const val IOS_BACK_SWIPE_NOTIFICATION = "ClickIOSBackSwipe"
