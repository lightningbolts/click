package compose.project.click.click

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.ComposeUIViewController

private object NoOpComposeHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
}

fun MainViewController() = ComposeUIViewController {
    if (shouldUseNoOpComposeHaptics()) {
        CompositionLocalProvider(
            LocalHapticFeedback provides remember { NoOpComposeHapticFeedback },
        ) {
            App()
        }
    } else {
        App()
    }
}