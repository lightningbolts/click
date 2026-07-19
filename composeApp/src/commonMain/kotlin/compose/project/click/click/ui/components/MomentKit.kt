package compose.project.click.click.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.platform.rememberReduceMotionEnabled
import compose.project.click.click.ui.theme.MotionTokens
import kotlinx.coroutines.delay

data class WaitingPulseValues(
    val scale: Float = 1f,
    val alpha: Float = 1f,
)

object StateCardMotion {
    val Enter: EnterTransition =
        fadeIn(animationSpec = MotionTokens.softEnterSpec()) +
            scaleIn(
                initialScale = 0.94f,
                animationSpec = MotionTokens.softEnterSpec(),
            )

    val Exit: ExitTransition =
        fadeOut(animationSpec = MotionTokens.softExitSpec()) +
            scaleOut(
                targetScale = 0.97f,
                animationSpec = MotionTokens.softExitSpec(),
            )
}

/**
 * A lifecycle-aware, visual-only waiting pulse. Haptics intentionally do not repeat while waiting.
 */
@Composable
fun rememberWaitingPulse(
    active: Boolean,
    durationMillis: Int = 800,
    scaleMax: Float = 1.15f,
    alphaMin: Float = 0.88f,
): WaitingPulseValues {
    val reduceMotion = rememberReduceMotionEnabled()
    val lifecycleOwner = LocalLifecycleOwner.current
    var isForeground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isForeground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!active || !isForeground || reduceMotion) return WaitingPulseValues()

    val transition = rememberInfiniteTransition(label = "moment_waiting_pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = scaleMax,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "moment_waiting_scale",
    )
    val alpha by transition.animateFloat(
        initialValue = alphaMin,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "moment_waiting_alpha",
    )
    return WaitingPulseValues(scale, alpha)
}

/**
 * One short, confetti-free success beat. A changed non-null [trigger] runs the beat exactly once.
 */
@Composable
fun SuccessBeat(
    trigger: Any?,
    modifier: Modifier = Modifier,
    lighterEcho: Boolean = false,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val reduceMotion = rememberReduceMotionEnabled()
    var scale by remember { mutableStateOf(1f) }

    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        if (hapticsEnabled) {
            PlatformHapticsPolicy.successNotification()
            if (!lighterEcho) PlatformHapticsPolicy.heavyImpact()
        }
        scale = if (reduceMotion) 1f else if (lighterEcho) 1.025f else 1.045f
        delay(if (lighterEcho) 90 else 120)
        scale = 1f
    }

    val animatedScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = scale,
        animationSpec = if (lighterEcho) {
            spring(dampingRatio = 0.86f, stiffness = 420f)
        } else {
            MotionTokens.emphasizedSuccessSpec()
        },
        label = "moment_success_scale",
    )
    Box(
        modifier = modifier.graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        },
    ) {
        content()
    }
}

@Composable
fun StateCardTransition(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduceMotion = rememberReduceMotionEnabled()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduceMotion) fadeIn(animationSpec = tween(120)) else StateCardMotion.Enter,
        exit = if (reduceMotion) fadeOut(animationSpec = tween(90)) else StateCardMotion.Exit,
    ) {
        content()
    }
}
