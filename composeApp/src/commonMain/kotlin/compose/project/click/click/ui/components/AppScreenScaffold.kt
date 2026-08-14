@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.encounter.EncounterTetherManager // pragma: allowlist secret
import compose.project.click.click.encounter.tetherCompassMessage // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import kotlinx.coroutines.delay

private const val FLOATING_HEADER_INSET_ITEM_KEY = "__floating_header_inset__"

/**
 * Standard tab-root layout: scrollable body extends under the floating nav bar, with bottom
 * content padding so every control stays reachable. A borderless large title floats at the top
 * and collapses to a semi-translucent glass bar as the user scrolls.
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
     * Space between the floating header bottom and the first list item.
     * When [verticalArrangement] is [Arrangement.spacedBy], that spacing is *also*
     * inserted after the header inset item — pass `desiredGap - spacedByAmount` so the
     * visible gap matches [desiredGap] (see Home search under greeting).
     */
    belowHeaderSpacing: Dp = AppScreenDefaults.SectionSpacing,
    horizontalPadding: Dp = AppScreenDefaults.HorizontalPadding,
    lazyListState: LazyListState = rememberLazyListState(),
    headerBelowContent: @Composable (() -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit,
) {
    val statusBarTop = rememberStatusBarTopPadding()
    val bottomChrome = rememberBottomChromePadding()
    val density = LocalDensity.current
    val thresholdPx = with(density) { AppScreenDefaults.HeaderCollapseScrollThreshold.roundToPx() }
    val compactHeaderClearance = rememberCompactFloatingHeaderClearance(statusBarTop)
    val collapseFraction by remember(lazyListState, thresholdPx) {
        derivedStateOf {
            if (showFloatingHeader) {
                lazyListState.headerCollapseFraction(thresholdPx)
            } else {
                0f
            }
        }
    }
    val (measuredTopPadding, headerMeasureModifier) =
        rememberFloatingHeaderTopPadding(collapseFraction, statusBarTop, belowHeaderSpacing)
    val topContentPadding =
        if (showFloatingHeader) {
            measuredTopPadding
        } else {
            statusBarTop + 16.dp
        }
    val expandedHeaderSlack =
        remember(topContentPadding, compactHeaderClearance) {
            (topContentPadding - compactHeaderClearance).coerceAtLeast(0.dp)
        }
    val headerHidden =
        if (showFloatingHeader) {
            rememberLazyFloatingHeaderHidden(
                lazyListState = lazyListState,
                expandedHeaderSlack = expandedHeaderSlack,
                density = density,
                thresholdPx = thresholdPx,
            )
        } else {
            true
        }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = verticalArrangement,
            contentPadding =
                PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    bottom = bottomChrome,
                ),
        ) {
            item(key = FLOATING_HEADER_INSET_ITEM_KEY) {
                Spacer(Modifier.height(topContentPadding))
            }
            content()
        }

        if (showFloatingHeader) {
            FloatingHeaderOverlay(
                hidden = headerHidden,
                horizontalPadding = horizontalPadding,
                headerMeasureModifier = headerMeasureModifier,
                collapseFraction = collapseFraction,
            ) {
                LiquidGlassPageHeader(
                    title = title,
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
                    collapseFraction = collapseFraction,
                )
                headerBelowContent?.invoke()
            }
        }
    }
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
    val scrollState = rememberScrollState()
    val statusBarTop = rememberStatusBarTopPadding()
    val bottomChrome = rememberBottomChromePadding()
    val density = LocalDensity.current
    val thresholdPx = with(density) { AppScreenDefaults.HeaderCollapseScrollThreshold.roundToPx() }
    val compactHeaderClearance = rememberCompactFloatingHeaderClearance(statusBarTop)
    val collapseFraction by remember(scrollState, thresholdPx) {
        derivedStateOf {
            computeHeaderCollapseFraction(
                scrollOffsetPx = scrollState.value,
                firstVisibleItemIndex = 0,
                thresholdPx = thresholdPx,
            )
        }
    }
    val (topContentPadding, headerMeasureModifier) =
        rememberFloatingHeaderTopPadding(collapseFraction, statusBarTop)
    val expandedHeaderSlack =
        remember(topContentPadding, compactHeaderClearance) {
            (topContentPadding - compactHeaderClearance).coerceAtLeast(0.dp)
        }
    val headerHidden =
        rememberScrollFloatingHeaderHidden(
            scrollState = scrollState,
            expandedHeaderSlack = expandedHeaderSlack,
            density = density,
            thresholdPx = thresholdPx,
        )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = topContentPadding,
                        bottom = bottomChrome,
                    ),
        ) {
            content(Modifier.fillMaxWidth())
        }

        FloatingHeaderOverlay(
            hidden = headerHidden,
            horizontalPadding = horizontalPadding,
            headerMeasureModifier = headerMeasureModifier,
            collapseFraction = collapseFraction,
        ) {
            LiquidGlassPageHeader(
                title = title,
                subtitle = subtitle,
                presenceOnline = presenceOnline,
                navigationIcon = navigationIcon,
                actions = actions,
                collapseFraction = collapseFraction,
            )
        }
    }
}

/**
 * Scroll offset → 0 (large) … 1 (compact) for tab-root floating headers.
 * [thresholdPx] should be [AppScreenDefaults.HeaderCollapseScrollThreshold] converted with [Density].
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

private const val FLOATING_HEADER_HIDE_HYSTERESIS_PX = 32f

@Composable
private fun rememberFloatingHeaderHidden(
    collapseFraction: Float,
    scrollPx: Float,
    slackPx: Float,
    forceHidden: Boolean = false,
): Boolean {
    var hidden by remember { mutableStateOf(false) }
    SideEffect {
        when {
            forceHidden -> hidden = true
            hidden -> {
                if (scrollPx <= slackPx - FLOATING_HEADER_HIDE_HYSTERESIS_PX) {
                    hidden = false
                }
            }
            collapseFraction >= 1f && scrollPx >= slackPx -> hidden = true
        }
    }
    return hidden
}

@Composable
private fun rememberScrollFloatingHeaderHidden(
    scrollState: androidx.compose.foundation.ScrollState,
    expandedHeaderSlack: Dp,
    density: Density,
    thresholdPx: Int,
): Boolean {
    val collapseFraction by remember(scrollState, thresholdPx) {
        derivedStateOf {
            computeHeaderCollapseFraction(
                scrollOffsetPx = scrollState.value,
                firstVisibleItemIndex = 0,
                thresholdPx = thresholdPx,
            )
        }
    }
    val scrollPx by remember(scrollState) {
        derivedStateOf { scrollState.value.toFloat() }
    }
    val slackPx = with(density) { expandedHeaderSlack.toPx() }
    return rememberFloatingHeaderHidden(
        collapseFraction = collapseFraction,
        scrollPx = scrollPx,
        slackPx = slackPx,
    )
}

@Composable
private fun rememberLazyFloatingHeaderHidden(
    lazyListState: LazyListState,
    expandedHeaderSlack: Dp,
    density: Density,
    thresholdPx: Int,
): Boolean {
    val collapseFraction by remember(lazyListState, thresholdPx) {
        derivedStateOf { lazyListState.headerCollapseFraction(thresholdPx) }
    }
    val scrollPx by remember(lazyListState) {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex > 0) {
                Float.MAX_VALUE
            } else {
                lazyListState.firstVisibleItemScrollOffset.toFloat()
            }
        }
    }
    val forceHidden by remember(lazyListState) {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
    }
    val slackPx = with(density) { expandedHeaderSlack.toPx() }
    return rememberFloatingHeaderHidden(
        collapseFraction = collapseFraction,
        scrollPx = scrollPx,
        slackPx = slackPx,
        forceHidden = forceHidden,
    )
}

@Composable
private fun BoxScope.FloatingHeaderOverlay(
    hidden: Boolean,
    horizontalPadding: Dp,
    headerMeasureModifier: Modifier,
    collapseFraction: Float,
    headerContent: @Composable () -> Unit,
) {
    if (hidden) return
    val showGlass = collapseFraction > 0.01f
    Box(
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f)
                .fillMaxWidth(),
    ) {
        if (showGlass) {
            HeaderGlassBackdrop(
                modifier = Modifier.matchParentSize(),
                collapseFraction = collapseFraction,
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .floatingHeaderStatusBarPadding()
                    .padding(start = horizontalPadding, end = horizontalPadding)
                    .then(headerMeasureModifier),
        ) {
            headerContent()
        }
    }
}

/**
 * Scrollable screens (e.g. Add Click) with a floating header that collapses on scroll.
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
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val thresholdPx = with(density) { AppScreenDefaults.HeaderCollapseScrollThreshold.roundToPx() }
    val statusBarTop = rememberStatusBarTopPadding()
    val bottomChrome = rememberBottomChromePadding()
    val collapseFraction by remember(scrollState, thresholdPx) {
        derivedStateOf {
            computeHeaderCollapseFraction(
                scrollOffsetPx = scrollState.value,
                firstVisibleItemIndex = 0,
                thresholdPx = thresholdPx,
            )
        }
    }
    val (topContentPadding, headerMeasureModifier) =
        rememberFloatingHeaderTopPadding(
            collapseFraction = collapseFraction,
            statusBarTop = statusBarTop,
        )
    val compactHeaderClearance = rememberCompactFloatingHeaderClearance(statusBarTop)
    val expandedHeaderSlack =
        remember(topContentPadding, compactHeaderClearance) {
            (topContentPadding - compactHeaderClearance).coerceAtLeast(0.dp)
        }
    val headerHidden =
        rememberScrollFloatingHeaderHidden(
            scrollState = scrollState,
            expandedHeaderSlack = expandedHeaderSlack,
            density = density,
            thresholdPx = thresholdPx,
        )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        bottom = bottomChrome,
                    ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(topContentPadding))
                content(Modifier.fillMaxWidth())
            }
        }

        FloatingHeaderOverlay(
            hidden = headerHidden,
            horizontalPadding = horizontalPadding,
            headerMeasureModifier = headerMeasureModifier,
            collapseFraction = collapseFraction,
        ) {
            LiquidGlassPageHeader(
                title = title,
                subtitle = subtitle,
                presenceOnline = presenceOnline,
                navigationIcon = navigationIcon,
                actions = actions,
                collapseFraction = collapseFraction,
            )
        }
    }
}

/**
 * Root-level tether compass toast — mount once from [compose.project.click.click.App] so pings // pragma: allowlist secret
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
