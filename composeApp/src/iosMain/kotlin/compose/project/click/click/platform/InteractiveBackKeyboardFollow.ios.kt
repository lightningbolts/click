@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package compose.project.click.click.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow

/**
 * Translates non-key windows above the normal level (keyboard / input accessory stacks).
 */
actual object InteractiveBackKeyboardFollow {
    private val transformedWindows = mutableListOf<UIWindow>()

    actual fun setDismissProgress(progress: Float) {
        resetInner()
        val app = UIApplication.sharedApplication
        @Suppress("DEPRECATION")
        val rawWindows = app.windows ?: return
        @Suppress("UNCHECKED_CAST")
        val windows = rawWindows as List<Any>
        val keyWindow = app.keyWindow
        val clamped = progress.coerceIn(0f, 1f)
        for (item in windows) {
            val w = item as? UIWindow ?: continue
            if (w == keyWindow) continue
            if (w.windowLevel <= 0.0) continue
            val dismissTravel = minOf(w.bounds.useContents { size.height } * 0.42, 360.0)
            w.setTransform(CGAffineTransformMakeTranslation(0.0, dismissTravel * clamped))
            transformedWindows.add(w)
        }
    }

    actual fun reset() {
        resetInner()
    }

    private fun resetInner() {
        for (w in transformedWindows) {
            w.setTransform(CGAffineTransformMakeTranslation(0.0, 0.0))
        }
        transformedWindows.clear()
    }
}
