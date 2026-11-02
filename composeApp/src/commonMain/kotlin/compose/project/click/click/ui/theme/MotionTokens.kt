@file:Suppress(
    "ktlint:standard:property-naming",
)

package compose.project.click.click.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.unit.dp

/**
 * The small, shared motion vocabulary for Click UI.
 *
 * Every launch-critical animation should map to exactly one category: Navigation, Press,
 * Content, Success, Destructive, or Gesture. Gesture thresholds and release physics stay
 * outside this file — sheet commit distance/velocity remain owned by
 * `GlassSheetGesturePhysics`, swipe-back by `InteractiveSwipeBackContainer`, and pile drag
 * by the existing card-stack implementation.
 *
 * Reduced-motion is a composable platform preference (`rememberReduceMotionEnabled`);
 * callers replace spatial springs/pulses with [reduceMotionEnter] / [reduceMotionExit]
 * or an immediate state change.
 */
object MotionTokens {
    /**
     * Tween durations (ms). iOS `ClickMotion` (`Click/DesignSystem/Motion.swift`) is spring-based
     * and has no duration scale, so the shared cross-platform scale from web `DESIGN.md`
     * (`--d-press` 120, `--d-fast` 160, `--d-base` 240, `--d-slow` 360) is used, with
     * [Emphasized] taken from the response of iOS `ClickMotion.reveal` (0.5 s).
     */
    object Duration {
        const val Instant = 120
        const val Fast = 160
        const val Standard = 240
        const val Deliberate = 360
        const val Emphasized = 500
    }

    /** Live-state pulse cycles only — never use for ordinary content transitions. */
    object Pulse {
        const val Gentle = 1200
        const val Scanning = 1800
        const val Shimmer = 900
    }

    /**
     * One press scale for every control size (web `.press`, scale 0.97; the same 0.97 iOS uses for
     * its press-in transitions). The three names are kept so call sites read by role.
     *
     * The spring is iOS `ClickMotion.press` (`spring(response: 0.2, dampingFraction: 0.7)`), with
     * stiffness = (2π / response)² for unit mass.
     */
    object PressScale {
        const val Scale = 0.97f
        const val IconPressedScale = Scale
        const val ButtonPressedScale = Scale
        const val CardPressedScale = Scale

        /** iOS `ClickMotion.press` response, in seconds. */
        const val IosResponseSeconds = 0.2f
        const val DampingRatio = 0.7f
        const val Stiffness = 986.96f
    }

    object Content {
        val EnterOffset = 8.dp
    }

    object SoftEnter {
        const val DampingRatio = Spring.DampingRatioNoBouncy
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

    fun pressScaleSpec(): SpringSpec<Float> =
        spring(
            dampingRatio = PressScale.DampingRatio,
            stiffness = PressScale.Stiffness,
        )

    fun <T> contentEnterSpec(): TweenSpec<T> = tween(durationMillis = Duration.Standard, easing = FastOutSlowInEasing)

    fun <T> contentExitSpec(): TweenSpec<T> = tween(durationMillis = Duration.Fast, easing = FastOutSlowInEasing)

    fun <T> crossfadeSpec(): TweenSpec<T> = tween(durationMillis = Duration.Standard, easing = FastOutSlowInEasing)

    fun <T> expandSpec(): TweenSpec<T> = tween(durationMillis = Duration.Deliberate, easing = FastOutSlowInEasing)

    fun <T> fadeSpec(): TweenSpec<T> = tween(durationMillis = Duration.Fast, easing = FastOutSlowInEasing)

    /** Ordinary overlay/content enter — tween, no bounce. */
    fun <T> softEnterSpec(): TweenSpec<T> = contentEnterSpec()

    /** Ordinary overlay/content exit — tween, no bounce. */
    fun <T> softExitSpec(): TweenSpec<T> = contentExitSpec()

    fun <T> successSpec(): SpringSpec<T> = emphasizedSuccessSpec()

    fun <T> emphasizedSuccessSpec(): SpringSpec<T> =
        spring(
            dampingRatio = EmphasizedSuccess.DampingRatio,
            stiffness = EmphasizedSuccess.Stiffness,
        )

    fun <T> destructiveSpec(): SpringSpec<T> =
        spring(
            dampingRatio = Destructive.DampingRatio,
            stiffness = Destructive.Stiffness,
        )

    fun contentFadeIn(): EnterTransition = fadeIn(animationSpec = contentEnterSpec())

    fun contentFadeOut(): ExitTransition = fadeOut(animationSpec = contentExitSpec())

    fun reduceMotionEnter(): EnterTransition = fadeIn(animationSpec = fadeSpec())

    fun reduceMotionExit(): ExitTransition = fadeOut(animationSpec = tween(durationMillis = Duration.Instant, easing = FastOutSlowInEasing))

    object PileSnap {
        const val DampingRatio = Spring.DampingRatioMediumBouncy
        const val Stiffness = Spring.StiffnessLow
    }

    fun <T> pileSnapSpec(): SpringSpec<T> =
        spring(
            dampingRatio = PileSnap.DampingRatio,
            stiffness = PileSnap.Stiffness,
        )
}
