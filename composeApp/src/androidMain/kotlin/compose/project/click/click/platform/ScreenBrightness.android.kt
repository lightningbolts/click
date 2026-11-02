package compose.project.click.click.platform

import android.view.WindowManager

actual fun setCodeDisplayBrightness(enabled: Boolean) {
    val activity = AndroidActivityRuntime.currentActivity() ?: return
    activity.runOnUiThread {
        val window = activity.window ?: return@runOnUiThread
        val attrs = window.attributes
        attrs.screenBrightness =
            if (enabled) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = attrs
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
