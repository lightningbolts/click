package compose.project.click.click.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * The small, shared motion vocabulary for Click UI.
 *
 * These values catalogue physics that were already shipping; they do not introduce a second
 * gesture-physics authority. Sheet commit distance and velocity remain owned by
 * `GlassSheetGesturePhysics`, and swipe-back thresholds remain owned by
 * `InteractiveSwipeBackContainer`.
 *
 * Keep motion meaningful: presses may use [PressScale], ordinary overlays use
 * [SoftEnter]/[SoftExit], successful commits use [EmphasizedSuccess], and destructive actions use
 * [Destructive]. Never add haptics to scrolling. Product haptic semantics are light for send,
 * success then heavy for connect success, light/medium for call accept/end, and heavy for a
 * destructive confirmation.
 *
 * Reduced-motion is a composable platform preference (`rememberReduceMotionEnabled`); callers
 * select short fades instead of these spatial springs when it is enabled.
 */
object MotionTokens {
    object PressScale {
        /** Strong enough to read on a light tap; still bouncy on release. */
        const val PressedScale = 0.92f
        const val DampingRatio = Spring.DampingRatioMediumBouncy
        const val Stiffness = Spring.StiffnessMedium
    }

    object SoftEnter {
        const val DampingRatio = Spring.DampingRatioLowBouncy
        const val Stiffness = Spring.StiffnessMediumLow
    }

    object SoftExit {
        const val DampingRatio = Spring.DampingRatioNoBouncy
        const val Stiffness = Spring.StiffnessMedium
    }

    object EmphasizedSuccess {
        const val DampingRatio = 0.72f
        const val Stiffness = 360f
    }

    object Destructive {
        const val DampingRatio = Spring.DampingRatioNoBouncy
        const val Stiffness = Spring.StiffnessHigh
    }

    fun pressScaleSpec(): SpringSpec<Float> = spring(
        dampingRatio = PressScale.DampingRatio,
        stiffness = PressScale.Stiffness,
    )

    fun <T> softEnterSpec(): SpringSpec<T> = spring(
        dampingRatio = SoftEnter.DampingRatio,
        stiffness = SoftEnter.Stiffness,
    )

    fun <T> softExitSpec(): SpringSpec<T> = spring(
        dampingRatio = SoftExit.DampingRatio,
        stiffness = SoftExit.Stiffness,
    )

    fun <T> emphasizedSuccessSpec(): SpringSpec<T> = spring(
        dampingRatio = EmphasizedSuccess.DampingRatio,
        stiffness = EmphasizedSuccess.Stiffness,
    )

    fun <T> destructiveSpec(): SpringSpec<T> = spring(
        dampingRatio = Destructive.DampingRatio,
        stiffness = Destructive.Stiffness,
    )

    object PileSnap {
        const val DampingRatio = Spring.DampingRatioMediumBouncy
        const val Stiffness = Spring.StiffnessLow
    }

    fun <T> pileSnapSpec(): SpringSpec<T> = spring(
        dampingRatio = PileSnap.DampingRatio,
        stiffness = PileSnap.Stiffness,
    )
}
