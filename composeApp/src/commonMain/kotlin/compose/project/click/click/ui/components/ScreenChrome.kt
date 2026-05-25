package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
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
 * Animated bottom inset = max(home indicator / tab stack, IME). Uses the platform inset union so
 * keyboard motion tracks the system curve without stacking nav + keyboard padding (which leaves a gap).
 */
private fun Modifier.chatBottomInsetUnion(extraBottom: Dp = 0.dp): Modifier = composed {
    val style = LocalPlatformStyle.current
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    if (!style.isIOS) {
        val ime = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        // adjustResize already lifts content; extra bottom pad while IME is open double-counts.
        val bottom = if (ime > 0.dp) 0.dp else nav + extraBottom
        return@composed Modifier.padding(bottom = bottom)
    }
    Modifier
        .windowInsetsPadding(
            WindowInsets.navigationBars
                .only(WindowInsetsSides.Bottom)
                .union(WindowInsets.ime.only(WindowInsetsSides.Bottom)),
        )
        .then(if (extraBottom > 0.dp) Modifier.padding(bottom = extraBottom) else Modifier)
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
 * Wrap the chat thread + composer column (below the fixed header). The whole block shrinks with the
 * keyboard so the input row stays flush on top of the IME — same model as iMessage / WhatsApp.
 *
 * Android [adjustResize] already lifts the window; only home-indicator padding is applied.
 */
fun Modifier.chatThreadKeyboardDock(extraBottom: Dp = 0.dp): Modifier =
    chatBottomInsetUnion(extraBottom)

/**
 * Legacy alias — prefer applying [chatThreadKeyboardDock] on the thread+composer container and
 * leaving the composer strip itself unpadded.
 */
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
