@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable
import kotlin.math.pow

/**
 * While a code is shown (Click Pass, My QR): eases the screen to full brightness and keeps it
 * awake; restores both on leaving, on `ON_STOP`, or when [active] turns false (a sheet collapsed
 * to its partial state). Holders are reference-counted, so overlapping screens restore the user's
 * level once (sheet 08 G3-29).
 */
@Composable
expect fun KeepScreenBrightAndAwake(active: Boolean = true)

/** Keep-awake only (the door scanner). */
@Composable
expect fun KeepScreenAwake(active: Boolean = true)

/** 14 steps × 16 ms, cubic ease-out (iOS `boostsScreenBrightness`). */
const val BRIGHTNESS_EASE_STEPS = 14
const val BRIGHTNESS_EASE_STEP_MS = 16L

/** The brightness at [step] of [BRIGHTNESS_EASE_STEPS] going from [from] to [to]. */
fun easedBrightness(
    from: Float,
    to: Float,
    step: Int,
    steps: Int = BRIGHTNESS_EASE_STEPS,
): Float {
    val t = (step.toFloat() / steps).coerceIn(0f, 1f)
    val eased = 1f - (1f - t).pow(3)
    return from + (to - from) * eased
}

/**
 * Reference-counts brightness holders: the first [acquire] remembers the level to restore, the
 * last [release] says to restore it. Pure, so the platform side only applies what it returns.
 */
class BrightnessHolderCount {
    var holders: Int = 0
        private set

    /** True when this is the first holder (start the boost). */
    fun acquire(): Boolean {
        holders += 1
        return holders == 1
    }

    /** True when this was the last holder (restore the user's level). */
    fun release(): Boolean {
        if (holders == 0) return false
        holders -= 1
        return holders == 0
    }
}
