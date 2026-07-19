package compose.project.click.click.ui.chat

import androidx.compose.runtime.MutableFloatState

internal actual class ComposerLiftAnimator actual constructor() {
    actual fun animateTo(
        liftPxState: MutableFloatState,
        targetPx: Float,
        durationMs: Int,
        curve: Int,
        density: Float,
    ) {
        liftPxState.floatValue = targetPx
    }

    actual fun snapTo(liftPxState: MutableFloatState, targetPx: Float) {
        liftPxState.floatValue = targetPx
    }

    actual fun cancel() = Unit

    actual fun dispose() = Unit
}
