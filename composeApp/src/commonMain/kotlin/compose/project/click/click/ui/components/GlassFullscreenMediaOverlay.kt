@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret

/**
 * Full-screen media lightbox.
 *
 * Drawn in the caller’s Compose tree (not a window [androidx.compose.ui.window.Popup] or
 * [androidx.compose.ui.window.Dialog]). A Popup on iOS sits above the host `UINavigationBar`,
 * which hid the liquid-glass close control and left a header-height sliver of the chat
 * underneath. Click Drops uses the same in-tree cover + exclusive overlay bind.
 *
 * iOS close and trailing actions retarget the existing overlay [ApplyOverlayMediaChrome]
 * (same glass controls as chat). A second exclusive bind with an empty title rebuilt the
 * bar and rematerialized Liquid Glass. Android uses [MediaLightboxTopChrome]. Cover is not
 * used: covering then releasing hid the bar for a frame.
 */
@Composable
fun GlassFullscreenMediaOverlay(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrimAlpha: Float = GlassSheetTokens.ScrimBaseAlpha,
    motion: UnifiedPopupMotion = UnifiedPopupMotion.Media,
    nativeTrailingActions: List<NativeChromeAction> = emptyList(),
    content: @Composable () -> Unit,
) {
    val transitionState = remember { MutableTransitionState(false) }
    var userDismissPending by remember { mutableStateOf(false) }

    LaunchedEffect(visible, userDismissPending) {
        if (userDismissPending) return@LaunchedEffect
        transitionState.targetState = visible
    }

    fun requestDismiss() {
        if (!transitionState.targetState) return
        userDismissPending = true
        transitionState.targetState = false
    }

    LaunchedEffect(transitionState.isIdle, transitionState.currentState, transitionState.targetState) {
        if (
            transitionState.isIdle &&
            !transitionState.currentState &&
            !transitionState.targetState &&
            userDismissPending
        ) {
            userDismissPending = false
            onDismissRequest()
        }
    }

    if (!transitionState.currentState && !transitionState.targetState && transitionState.isIdle) {
        return
    }

    val coverNativeTabBar =
        OverlayExclusiveBindPolicy.shouldCoverNativeTabBarForMedia(
            isIOS = LocalPlatformStyle.current.isIOS,
        )
    CompositionLocalProvider(LocalNativeChromeActive provides true) {
        ApplyOverlayMediaChrome(
            active = true,
            onClose = ::requestDismiss,
            trailing = nativeTrailingActions,
        )
    }
    DisposableEffect(coverNativeTabBar) {
        if (coverNativeTabBar) AppScreenChromeState.acquireNativeTabBarCover()
        onDispose {
            if (coverNativeTabBar) AppScreenChromeState.releaseNativeTabBarCover()
        }
    }
    PlatformBackHandler(enabled = transitionState.targetState, onBack = ::requestDismiss)

    val fadeInSpec = tween<Float>(durationMillis = motion.fadeInMillis, easing = FastOutSlowInEasing)
    val fadeOutSpec = tween<Float>(durationMillis = motion.fadeOutMillis, easing = FastOutSlowInEasing)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .zIndex(UnifiedPopupTokens.OverlayZIndex),
    ) {
        AnimatedVisibility(
            visibleState = transitionState,
            enter = fadeIn(animationSpec = fadeInSpec),
            exit = fadeOut(animationSpec = fadeOutSpec),
            label = "media_overlay_scrim",
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha)),
            )
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = fadeIn(animationSpec = fadeInSpec),
                exit = fadeOut(animationSpec = fadeOutSpec),
                label = "media_overlay_content",
            ) {
                content()
            }
        }
    }
}

/**
 * Close control for photo / media lightboxes. Same 40pt liquid-glass circle as compact native
 * chrome, inset from the safe drawing edge — not stacked under the chat header.
 *
 * iOS hides this Compose control; the overlay navigation bar owns the real glass xmark.
 */
@Composable
fun MediaLightboxTopChrome(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showClose: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val isIOS = LocalPlatformStyle.current.isIOS
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val safeTop = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val insetTop = maxOf(statusTop, safeTop)
    val resolvedTop =
        if (insetTop > 0.dp) {
            insetTop
        } else if (isIOS) {
            59.dp
        } else {
            0.dp
        }
    val buttonSize = NativeHeaderMetrics.ChromeButtonSizePt.dp
    val topGutter =
        ((NativeHeaderMetrics.CompactBarHeightPt - NativeHeaderMetrics.ChromeButtonSizePt) / 2.0).dp
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .zIndex(2f)
                .padding(
                    start = NativeHeaderMetrics.LeadingInsetPt.dp,
                    end = NativeHeaderMetrics.TrailingInsetPt.dp,
                    top = resolvedTop + topGutter,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showClose) {
            ClickCircularGlassIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Close",
                onClick = onClose,
                size = buttonSize,
                tint = Color.White,
            )
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

internal fun mediaLightboxShareActions(
    onSave: () -> Unit,
    onShare: () -> Unit,
): List<NativeChromeAction> =
    listOf(
        NativeChromeAction(
            sfSymbol = "square.and.arrow.down",
            contentDescription = "Save",
            onClick = onSave,
        ),
        NativeChromeAction(
            sfSymbol = "square.and.arrow.up",
            contentDescription = "Share",
            onClick = onShare,
        ),
    )

@Composable
internal fun MediaLightboxSaveShareTrailing(
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    TextButton(onClick = onSave) {
        Text("Save", color = Color.White)
    }
    TextButton(onClick = onShare) {
        Text("Share", color = Color.White)
    }
}
