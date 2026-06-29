package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.LocalPlatformStyle

private val AndroidStatusBarFallback = 24.dp

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

/**
 * Reliable top safe-area inset for floating headers.
 * iOS: statusBars only (avoids safeDrawing/IME coupling).
 * Android: max(statusBars, safeDrawing top) with fallback for first-frame zero insets.
 */
@Composable
fun rememberStatusBarTopPadding(): Dp {
    val style = LocalPlatformStyle.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    if (style.isIOS) return statusBarTop

    val safeTop = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Top)
        .asPaddingValues()
        .calculateTopPadding()
    val measured = maxOf(statusBarTop, safeTop)
    return if (measured < 1.dp) AndroidStatusBarFallback else measured
}

/** Declarative status-bar padding for overlay headers (Android cutout-safe). */
fun Modifier.floatingHeaderStatusBarPadding(): Modifier = composed {
    val style = LocalPlatformStyle.current
    if (style.isIOS) {
        Modifier.windowInsetsPadding(WindowInsets.statusBars)
    } else {
        Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        )
    }
}

/**
 * Measures expanded header body height (excluding status bar) so scroll content clears
 * multi-line subtitles and accessibility font scaling.
 *
 * [measureModifier] must be applied to the header **body** only — not the status-bar inset
 * wrapper — so [statusBarTop] is not double-counted in list top padding.
 */
@Composable
fun rememberFloatingHeaderTopPadding(
    collapseFraction: Float,
    statusBarTop: Dp = rememberStatusBarTopPadding(),
): Pair<Dp, Modifier> {
    val density = LocalDensity.current
    var expandedHeaderBodyHeight by remember {
        mutableStateOf(AppScreenDefaults.FloatingHeaderLargeHeight)
    }
    var hasLockedExpandedHeight by remember { mutableStateOf(false) }
    val measureModifier = Modifier.onGloballyPositioned { coordinates ->
        if (!hasLockedExpandedHeight && collapseFraction < 0.05f) {
            val measuredBody = with(density) { coordinates.size.height.toDp() }
            if (measuredBody >= AppScreenDefaults.FloatingHeaderCompactHeight) {
                expandedHeaderBodyHeight = measuredBody
                hasLockedExpandedHeight = true
            }
        }
    }
    // Fixed expanded inset — overlay header collapses visually; do not shrink this during
    // scroll (causes jitter on both LazyColumn and verticalScroll screens).
    val topPadding = statusBarTop + expandedHeaderBodyHeight + AppScreenDefaults.SectionSpacing
    return topPadding to measureModifier
}

/** Minimum top inset for scroll content on collapsing-header screens (compact pill + safe area). */
@Composable
fun rememberCompactFloatingHeaderClearance(
    statusBarTop: Dp = rememberStatusBarTopPadding(),
): Dp =
    statusBarTop + AppScreenDefaults.FloatingHeaderCompactHeight + AppScreenDefaults.SectionSpacing

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
 * Smooth keyboard dock.
 *
 * The bottom chrome padding is static. Android keeps Compose's optimized IME inset placement,
 * while iOS can pass a native keyboard lift and move the already-measured block on a graphics
 * layer, avoiding per-frame chat relayout.
 */
private fun Modifier.chatBottomInsetUnion(
    extraBottom: Dp = 0.dp,
    nativeKeyboardLiftPx: Float? = null,
): Modifier = composed {
    val density = LocalDensity.current
    val style = LocalPlatformStyle.current
    val imeInsets = WindowInsets.ime
    val navInsets = WindowInsets.navigationBars
    val navBottomPx = navInsets.getBottom(density)
    val navBottomDp = with(density) { navBottomPx.toDp() }

    if (style.isIOS && nativeKeyboardLiftPx != null) {
        return@composed Modifier
            .padding(bottom = navBottomDp + extraBottom)
            .clipToBounds()
            .graphicsLayer {
                translationY = -nativeKeyboardLiftPx.coerceAtLeast(0f)
            }
    }

    Modifier
        .padding(bottom = navBottomDp + extraBottom)
        .clipToBounds()
        .offset {
            val imePx = imeInsets.getBottom(density)
            val navPx = navInsets.getBottom(density)
            val liftPx = (imePx - navPx).coerceAtLeast(0)
            IntOffset(0, -liftPx)
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
 * input row) above the keyboard without [imePadding] layout resize.
 */
fun Modifier.chatThreadKeyboardDock(
    extraBottom: Dp = 0.dp,
    nativeKeyboardLiftPx: Float? = null,
): Modifier = chatBottomInsetUnion(extraBottom, nativeKeyboardLiftPx)

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
