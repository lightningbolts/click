package compose.project.click.click

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import platform.UIKit.*

/**
 * Maps Compose [HapticFeedbackType] to `UIFeedbackGenerator` APIs (no Core Haptics / CHHapticEngine).
 *
 * iOS Compose previously routed haptics through Core Haptics pattern APIs, which can crash on device
 * when pattern resources are missing. This implementation stays on UIKit feedback generators only.
 */
internal class IosUIKitHapticFeedback : HapticFeedback {

    private val lightImpact = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val heavyImpact = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    private val notification = UINotificationFeedbackGenerator()

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        when (hapticFeedbackType) {
            HapticFeedbackType.Confirm ->
                notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)

            HapticFeedbackType.Reject ->
                notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)

            HapticFeedbackType.LongPress,
            HapticFeedbackType.GestureThresholdActivate,
            HapticFeedbackType.ToggleOn ->
                heavyImpact.impactOccurred()

            else ->
                lightImpact.impactOccurred()
        }
    }
}

actual fun shouldUseNoOpComposeHaptics(): Boolean = false
