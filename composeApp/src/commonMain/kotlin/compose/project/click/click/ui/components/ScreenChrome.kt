package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
    val IosTabBarContentHeight = 49.dp
    val AndroidNavBarContentHeight = 80.dp
    val FabGapAboveTabBar = 6.dp
}

/**
 * Distance from the bottom of the root content to the top of the floating tab bar.
 * Updated from [PlatformBottomBar] (iOS measures UITabBar frame; Android uses nav + bar height).
 */
@Stable
object AppScreenChromeState {
    var bottomChromeHeight by mutableStateOf(0.dp)
        private set

    fun updateBottomChromeHeight(height: Dp) {
        if (height > 0.dp) bottomChromeHeight = height
    }
}

/** Home-indicator / gesture inset + tab bar content — never less than this on iOS. */
@Composable
fun rememberIosTabBarStackHeight(): Dp {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return navigationBar + AppScreenDefaults.IosTabBarContentHeight
}

@Composable
fun rememberTabBarOverlayHeight(): Dp {
    val style = LocalPlatformStyle.current
    if (style.isIOS) {
        val minimum = rememberIosTabBarStackHeight()
        val measured = AppScreenChromeState.bottomChromeHeight
        return if (measured >= minimum) measured else minimum
    }
    val measured = AppScreenChromeState.bottomChromeHeight
    if (measured > 0.dp) return measured
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return navigationBar + AppScreenDefaults.AndroidNavBarContentHeight
}

@Composable
fun rememberBottomChromePadding(extra: Dp = AppScreenDefaults.ExtraScrollBottomPadding): Dp {
    return rememberTabBarOverlayHeight() + extra
}

/** FABs / map controls sit [AppScreenDefaults.FabGapAboveTabBar] above the tab bar top edge. */
@Composable
fun rememberFabAboveNavPadding(): Dp =
    rememberTabBarOverlayHeight() + AppScreenDefaults.FabGapAboveTabBar

/** Bottom inset for composers sitting above the floating tab bar. */
@Composable
fun rememberComposerBottomPadding(extra: Dp = 0.dp): Dp {
    return rememberTabBarOverlayHeight() + extra
}

/**
 * Smooth keyboard dock via placement-phase offset.
 *
 * Instead of applying animated padding (which changes measured height → triggers full remeasure of
 * every weighted child on every frame), this modifier:
 *   1. Applies **static** bottom padding for the nav bar (constant, no animation).
 *   2. Uses [clipToBounds] so content shifted above the container top is hidden (prevents
 *      overlapping the header during the slide).
 *   3. Uses [graphicsLayer] [translationY] (draw-phase) for `(IME - navBar)` px. Pair with an
 *      isolated message-list composable so visible bubbles are not recomposed each keyboard frame.
 */
private fun Modifier.chatBottomInsetUnion(extraBottom: Dp = 0.dp): Modifier = composed {
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val navInsets = WindowInsets.navigationBars
    val navBottomPx = navInsets.getBottom(density)
    val navBottomDp = with(density) { navBottomPx.toDp() }

    Modifier
        .padding(bottom = navBottomDp + extraBottom)
        .clipToBounds()
        .graphicsLayer {
            val imePx = imeInsets.getBottom(density)
            val navPx = navInsets.getBottom(density)
            translationY = -(imePx - navPx).coerceAtLeast(0).toFloat()
        }
}

/**
 * Pins the chat composer above the tab bar. On iOS uses [maxOf] tab stack height and IME inset.
 */
fun Modifier.chatComposerDock(extraBottom: Dp = 0.dp): Modifier = composed {
    val style = LocalPlatformStyle.current
    val tabStack = rememberTabBarOverlayHeight() + extraBottom
    if (!style.isIOS) {
        return@composed Modifier.padding(bottom = tabStack)
    }
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    Modifier.padding(bottom = maxOf(tabStack, imeBottom))
}

/**
 * Thread + composer column below the fixed chat header. Slides the **entire** block (messages and
 * input row) above the keyboard via draw-phase [translationY] without [imePadding] layout resize.
 */
fun Modifier.chatThreadKeyboardDock(extraBottom: Dp = 0.dp): Modifier =
    chatBottomInsetUnion(extraBottom)

/** @see chatThreadKeyboardDock */
fun Modifier.chatComposerDockEdgeToEdge(extraBottom: Dp = 0.dp): Modifier =
    chatBottomInsetUnion(extraBottom)

@Composable
fun rememberEdgeToEdgeBottomPadding(extra: Dp = 0.dp): Dp {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return navigationBar + extra
}

fun Modifier.bottomChromePadding(extra: Dp = AppScreenDefaults.ExtraScrollBottomPadding): Modifier = composed {
    Modifier.padding(bottom = rememberBottomChromePadding(extra))
}

fun Modifier.composerBottomPadding(extra: Dp = 0.dp): Modifier = composed {
    Modifier.chatComposerDock(extra)
}
