@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.encounter.EncounterTetherManager // pragma: allowlist secret
import compose.project.click.click.encounter.tetherCompassMessage // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import kotlinx.coroutines.delay

/**
 * Standard tab-root layout: scrollable body extends under the floating nav bar, with bottom
 * content padding so every control stays reachable. Header chrome is platform-native
 * ([NativeCollapsingScaffold]) — Material 3 LargeTopAppBar on Android, UINavigationController
 * large titles on iOS. Collapse is compact chrome, never hide.
 *
 * When [showFloatingHeader] is false, only status-bar top inset is applied — no title
 * island. Prefer [showFloatingHeader] true for tab roots (including Home’s greeting).
 */
@Composable
fun AppScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    presenceOnline: Boolean? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    onOpenSearch: (() -> Unit)? = null,
    showFloatingHeader: Boolean = true,
    /**
     * Space between the native header bottom and the first list item.
     * When [verticalArrangement] is [Arrangement.spacedBy], that spacing is *also*
     * inserted after the header inset — pass `desiredGap - spacedByAmount` so the
     * visible gap matches [desiredGap] (see Home search under greeting).
     */
    belowHeaderSpacing: Dp = AppScreenDefaults.SectionSpacing,
    horizontalPadding: Dp = AppScreenDefaults.HorizontalPadding,
    lazyListState: LazyListState = rememberLazyListState(),
    headerBelowContent: @Composable (() -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit,
) {
    NativeCollapsingScaffold(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        presenceOnline = presenceOnline,
        navigationIcon = navigationIcon,
        actions =
            if (onOpenSearch != null || actions != null) {
                {
                    if (onOpenSearch != null) {
                        HeaderSearchIconButton(onClick = onOpenSearch)
                    }
                    actions?.invoke(this)
                }
            } else {
                null
            },
        showHeader = showFloatingHeader,
        belowHeaderSpacing = belowHeaderSpacing,
        horizontalPadding = horizontalPadding,
        lazyListState = lazyListState,
        headerBelowContent = headerBelowContent,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * Same chrome as [AppScreenScaffold] for non-lazy column content.
 */
@Composable
fun AppScreenScaffoldScroll(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    presenceOnline: Boolean? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    horizontalPadding: Dp = AppScreenDefaults.HorizontalPadding,
    content: @Composable (Modifier) -> Unit,
) {
    NativeCollapsingScrollScaffold(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        presenceOnline = presenceOnline,
        navigationIcon = navigationIcon,
        actions = actions,
        horizontalPadding = horizontalPadding,
        content = content,
    )
}

/**
 * Scroll offset → 0 (large) … 1 (compact) for tab-root collapsing headers.
 * [thresholdPx] should be [AppScreenDefaults.HeaderCollapseScrollThreshold] converted with Density.
 *
 * This fraction drives native chrome (iOS companion UIScrollView / Android LargeTopAppBar).
 * It must never be used to hide the header.
 */
fun computeHeaderCollapseFraction(
    scrollOffsetPx: Int,
    firstVisibleItemIndex: Int,
    thresholdPx: Int,
): Float {
    val threshold = thresholdPx.coerceAtLeast(1)
    return when {
        firstVisibleItemIndex > 0 -> 1f
        else -> (scrollOffsetPx.toFloat() / threshold).coerceIn(0f, 1f)
    }
}

fun LazyListState.headerCollapseFraction(thresholdPx: Int): Float =
    computeHeaderCollapseFraction(
        scrollOffsetPx = firstVisibleItemScrollOffset,
        firstVisibleItemIndex = firstVisibleItemIndex,
        thresholdPx = thresholdPx,
    )

/**
 * Scrollable screens (e.g. Add Click) with a native collapsing header.
 */
@Composable
fun AppScreenWithFloatingHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    presenceOnline: Boolean? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    horizontalPadding: Dp = AppScreenDefaults.HorizontalPadding,
    content: @Composable (Modifier) -> Unit,
) {
    NativeCollapsingScrollScaffold(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        presenceOnline = presenceOnline,
        navigationIcon = navigationIcon,
        actions = actions,
        horizontalPadding = horizontalPadding,
        content = content,
    )
}

/**
 * Root-level tether compass toast — mount once from the app root so pings
 * surface on any tab, not only inside an open chat.
 */
@Composable
fun GlobalTetherOverlay(modifier: Modifier = Modifier) {
    val payload by EncounterTetherManager.activeTetherPayload.collectAsState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(payload) {
        toastMessage =
            payload?.let { ping ->
                val receiver = LocationService().getCurrentLocation()
                if (receiver != null) {
                    tetherCompassMessage(
                        senderName = ping.senderName,
                        receiverLat = receiver.latitude,
                        receiverLng = receiver.longitude,
                        senderLat = ping.latitude,
                        senderLng = ping.longitude,
                    )
                } else {
                    "${ping.senderName} pinged their tether"
                }
            }
    }

    LaunchedEffect(payload?.timestampMs) {
        val active = payload ?: return@LaunchedEffect
        delay(30_000L)
        if (EncounterTetherManager.activeTetherPayload.value?.timestampMs == active.timestampMs) {
            EncounterTetherManager.clearActiveTetherPayload()
        }
    }

    TetherCompassToast(
        message = toastMessage,
        modifier =
            modifier
                .padding(top = statusBarTop + 64.dp),
        onDismissed = { EncounterTetherManager.clearActiveTetherPayload() },
    )
}
