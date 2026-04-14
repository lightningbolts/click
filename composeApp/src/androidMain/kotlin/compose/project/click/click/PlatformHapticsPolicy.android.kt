package compose.project.click.click

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.lang.ref.WeakReference

private object AndroidHapticHost {
    private var viewRef: WeakReference<View>? = null

    fun bind(view: View?) {
        viewRef = view?.let { WeakReference(it) }
    }

    fun view(): View? = viewRef?.get()
}

actual object PlatformHapticsPolicy {
    actual fun lightImpact() {
        val v = AndroidHapticHost.view() ?: return
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    actual fun heavyImpact() {
        val v = AndroidHapticHost.view() ?: return
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    actual fun successNotification() {
        val v = AndroidHapticHost.view() ?: return
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        v.performHapticFeedback(flag)
    }
}

@Composable
actual fun BindPlatformHapticsToViewHierarchy() {
    val view = LocalView.current
    DisposableEffect(view) {
        AndroidHapticHost.bind(view)
        onDispose {
            if (AndroidHapticHost.view() === view) {
                AndroidHapticHost.bind(null)
            }
        }
    }
}

actual fun shouldUseNoOpComposeHaptics(): Boolean = false
