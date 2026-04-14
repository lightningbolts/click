package compose.project.click.click

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

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
        try {
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
        } catch (_: Throwable) {
        }
    }
}

private fun safeIosHaptic(block: () -> Unit) {
    try {
        block()
    } catch (_: Throwable) {
    }
}

actual object PlatformHapticsPolicy {
    private val lightImpactGen by lazy {
        UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    }
    private val heavyImpactGen by lazy {
        UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    }
    private val notificationGen by lazy { UINotificationFeedbackGenerator() }

    actual fun lightImpact() {
        safeIosHaptic {
            lightImpactGen.prepare()
            lightImpactGen.impactOccurred()
        }
    }

    actual fun heavyImpact() {
        safeIosHaptic {
            heavyImpactGen.prepare()
            heavyImpactGen.impactOccurred()
        }
    }

    actual fun successNotification() {
        safeIosHaptic {
            notificationGen.prepare()
            notificationGen.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
        }
    }
}

@Composable
actual fun BindPlatformHapticsToViewHierarchy() {
}

actual fun shouldUseNoOpComposeHaptics(): Boolean = false
