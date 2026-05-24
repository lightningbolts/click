package compose.project.click.click.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.LocalPlatformStyle

/** Shared horizontal gutter for tab-root screens. */
object AppScreenDefaults {
    val HorizontalPadding = 20.dp
    val SectionSpacing = 24.dp
    val HeaderCollapseScrollThreshold = 96
    val FloatingHeaderLargeHeight = 112.dp
    val FloatingHeaderCompactHeight = 52.dp
    val ExtraScrollBottomPadding = 16.dp
}

/**
 * Reports the height of the bottom tab bar / navigation chrome so scrollable content
 * can extend behind it while keeping the last items reachable.
 */
@Stable
object AppScreenChromeState {
    var bottomChromeHeight by mutableStateOf(80.dp)
        private set

    fun updateBottomChromeHeight(height: Dp) {
        if (height > 0.dp) bottomChromeHeight = height
    }
}

@Composable
fun rememberBottomChromePadding(extra: Dp = AppScreenDefaults.ExtraScrollBottomPadding): Dp {
    return AppScreenChromeState.bottomChromeHeight + extra
}

/** Bottom inset for composers and FABs sitting above the floating tab bar. */
@Composable
fun rememberComposerBottomPadding(extra: Dp = 0.dp): Dp {
    val bottomChrome = rememberBottomChromePadding(extra)
    val style = LocalPlatformStyle.current
    if (!style.isIOS) return bottomChrome
    val imeTarget = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val animatedIme by animateDpAsState(
        targetValue = imeTarget,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "composer_ime_pad",
    )
    return maxOf(bottomChrome, animatedIme)
}

fun Modifier.bottomChromePadding(extra: Dp = AppScreenDefaults.ExtraScrollBottomPadding): Modifier = composed {
    Modifier.padding(bottom = rememberBottomChromePadding(extra))
}

fun Modifier.composerBottomPadding(extra: Dp = 0.dp): Modifier = composed {
    Modifier.padding(bottom = rememberComposerBottomPadding(extra))
}
