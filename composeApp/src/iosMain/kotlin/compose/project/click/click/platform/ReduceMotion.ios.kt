package compose.project.click.click.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityIsReduceTransparencyEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification
import platform.UIKit.UIAccessibilityReduceTransparencyStatusDidChangeNotification

@Composable
actual fun rememberReduceMotionEnabled(): Boolean {
    var enabled by remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(
            name = UIAccessibilityReduceMotionStatusDidChangeNotification,
            `object` = null,
            queue = null,
        ) {
            enabled = UIAccessibilityIsReduceMotionEnabled()
        }
        onDispose { center.removeObserver(observer) }
    }
    return enabled
}

@Composable
actual fun rememberReduceTransparencyEnabled(): Boolean {
    var enabled by remember { mutableStateOf(UIAccessibilityIsReduceTransparencyEnabled()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(
            name = UIAccessibilityReduceTransparencyStatusDidChangeNotification,
            `object` = null,
            queue = null,
        ) {
            enabled = UIAccessibilityIsReduceTransparencyEnabled()
        }
        onDispose { center.removeObserver(observer) }
    }
    return enabled
}
