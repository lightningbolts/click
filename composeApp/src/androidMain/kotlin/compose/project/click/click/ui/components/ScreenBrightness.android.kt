@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/** Window-level brightness boost, shared by every holder on the main thread. */
private object ScreenBrightnessHolder {
    private val count = BrightnessHolderCount()
    private val handler = Handler(Looper.getMainLooper())
    private var saved: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    private var easing: Runnable? = null

    fun acquire(activity: Activity) {
        if (!count.acquire()) return
        val window = activity.window
        saved = window.attributes.screenBrightness
        val from =
            if (saved >= 0f) {
                saved
            } else {
                runCatching {
                    Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                }.getOrDefault(0.5f)
            }
        ease(activity, from.coerceIn(0f, 1f), 1f, step = 1)
    }

    private fun ease(
        activity: Activity,
        from: Float,
        to: Float,
        step: Int,
    ) {
        if (count.holders == 0) return
        setBrightness(activity, easedBrightness(from, to, step))
        if (step >= BRIGHTNESS_EASE_STEPS) {
            easing = null
            return
        }
        val next = Runnable { ease(activity, from, to, step + 1) }
        easing = next
        handler.postDelayed(next, BRIGHTNESS_EASE_STEP_MS)
    }

    fun release(activity: Activity) {
        if (!count.release()) return
        easing?.let(handler::removeCallbacks)
        easing = null
        setBrightness(activity, saved)
    }

    private fun setBrightness(
        activity: Activity,
        value: Float,
    ) {
        val window = activity.window
        val attrs = window.attributes
        attrs.screenBrightness = value
        window.attributes = attrs
    }
}

private object KeepAwakeHolder {
    private val count = BrightnessHolderCount()

    fun acquire(activity: Activity) {
        if (count.acquire()) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun release(activity: Activity) {
        if (count.release()) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun LifecycleHold(
    active: Boolean,
    acquire: (Activity) -> Unit,
    release: (Activity) -> Unit,
) {
    val activity = LocalContext.current.findActivity() ?: return
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(activity, lifecycleOwner, active) {
        var holding = false

        fun hold() {
            if (!holding) {
                holding = true
                acquire(activity)
            }
        }

        fun letGo() {
            if (holding) {
                holding = false
                release(activity)
            }
        }
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> if (active) hold()
                    Lifecycle.Event.ON_STOP -> letGo()
                    else -> Unit
                }
            }
        if (active && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) hold()
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            letGo()
        }
    }
}

@Composable
actual fun KeepScreenBrightAndAwake(active: Boolean) {
    LifecycleHold(active, ScreenBrightnessHolder::acquire, ScreenBrightnessHolder::release)
    LifecycleHold(active, KeepAwakeHolder::acquire, KeepAwakeHolder::release)
}

@Composable
actual fun KeepScreenAwake(active: Boolean) {
    LifecycleHold(active, KeepAwakeHolder::acquire, KeepAwakeHolder::release)
}
