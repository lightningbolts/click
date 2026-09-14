@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf

val LocalNativeChromeTransitionAlpha = staticCompositionLocalOf { 1f }

/** Native title content follows the destination transition; its glass controls stay mounted. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun AnimatedVisibilityScope.NativeChromeTransition(
    durationMillis: Int = 220,
    content: @Composable () -> Unit,
) {
    val alpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis) },
        label = "native_chrome_destination_alpha",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    CompositionLocalProvider(LocalNativeChromeTransitionAlpha provides alpha, content = content)
}
