@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click

import androidx.compose.runtime.Composable

/**
 * App-level haptics outside Compose [androidx.compose.ui.platform.LocalHapticFeedback].
 * Used for gesture thresholds, proximity success, and consistent long-press feedback.
 */
expect object PlatformHapticsPolicy {
    fun lightImpact()

    fun heavyImpact()

    fun successNotification()
}

/**
 * Binds the Android haptic host [android.view.View] from composition.
 * Call once near the root of [App].
 */
@Composable
expect fun BindPlatformHapticsToViewHierarchy()

/**
 * Semantic haptic mapping. Screens should call these instead of choosing light/heavy ad hoc.
 * Scrolling and ordinary navigation never fire haptics.
 */
fun hapticSendMessage() = PlatformHapticsPolicy.lightImpact()

fun hapticSmallCommit() = PlatformHapticsPolicy.lightImpact()

fun hapticTapDetected() = PlatformHapticsPolicy.lightImpact()

fun hapticTapConnected() {
    PlatformHapticsPolicy.successNotification()
    PlatformHapticsPolicy.heavyImpact()
}

fun hapticCallAccept() = PlatformHapticsPolicy.lightImpact()

fun hapticCallEnd() = PlatformHapticsPolicy.lightImpact()

fun hapticDestructiveConfirm() = PlatformHapticsPolicy.heavyImpact()
