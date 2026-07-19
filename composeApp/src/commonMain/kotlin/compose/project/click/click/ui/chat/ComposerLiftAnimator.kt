package compose.project.click.click.ui.chat

import androidx.compose.runtime.MutableFloatState

/**
 * Drives [liftPxState] in lockstep with the system keyboard.
 * iOS samples UIKit's presentation layer inside the keyboard's own UIView animation;
 * other platforms snap (Android uses WindowInsets.ime instead).
 */
internal expect class ComposerLiftAnimator() {
    fun animateTo(
        liftPxState: MutableFloatState,
        targetPx: Float,
        durationMs: Int,
        curve: Int,
        /** Compose density — UIKit animates in points; Compose lift is in px. */
        density: Float,
    )

    fun snapTo(liftPxState: MutableFloatState, targetPx: Float)

    fun cancel()

    fun dispose()
}
