package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import compose.project.click.click.encounter.EncounterTetherManager
import compose.project.click.click.encounter.tetherCompassMessage
import compose.project.click.click.utils.LocationService
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

private const val FloatingHeaderInsetItemKey = "__floating_header_inset__"

/**
 * Standard tab-root layout: scrollable body extends under the floating nav bar, with bottom
 * content padding so every control stays reachable. A liquid-glass header island floats at
 * the top and collapses as the user scrolls.
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
    horizontalPadding: Dp = AppScreenDefaults.HorizontalPadding,
    lazyListState: LazyListState = rememberLazyListState(),
    headerBelowContent: @Composable (() -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit,
) {
    val statusBarTop = rememberStatusBarTopPadding()
    val bottomChrome = rememberBottomChromePadding()
    val density = LocalDensity.current
    val compactHeaderClearance = rememberCompactFloatingHeaderClearance(statusBarTop)
    val collapseFraction by remember(lazyListState) {
        derivedStateOf { lazyListState.headerCollapseFraction() }
    }
    val (topContentPadding, headerMeasureModifier) =
        rememberFloatingHeaderTopPadding(collapseFraction, statusBarTop)
    val expandedHeaderSlack = remember(topContentPadding, compactHeaderClearance) {
        (topContentPadding - compactHeaderClearance).coerceAtLeast(0.dp)
    }
    val headerHidden = rememberLazyFloatingHeaderHidden(
        lazyListState = lazyListState,
        expandedHeaderSlack = expandedHeaderSlack,
        density = density,
    )

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = verticalArrangement,
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                bottom = bottomChrome,
            ),
        ) {
            item(key = FloatingHeaderInsetItemKey) {
                Spacer(Modifier.height(topContentPadding))
            }
            content()
        }

        FloatingHeaderOverlay(
            hidden = headerHidden,
            horizontalPadding = horizontalPadding,
            headerMeasureModifier = headerMeasureModifier,
        ) {
            LiquidGlassPageHeader(
                title = title,
                subtitle = subtitle,
                presenceOnline = presenceOnline,
                navigationIcon = navigationIcon,
                actions = if (onOpenSearch != null || actions != null) {
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
    val compactHeaderClearance = rememberCompactFloatingHeaderClearance(statusBarTop)
    val collapseFraction by remember(scrollState) {
        derivedStateOf { scrollState.headerCollapseFraction() }
    }
    val (topContentPadding, headerMeasureModifier) =
        rememberFloatingHeaderTopPadding(collapseFraction, statusBarTop)
    val expandedHeaderSlack = remember(topContentPadding, compactHeaderClearance) {
        (topContentPadding - compactHeaderClearance).coerceAtLeast(0.dp)
    }
    val headerHidden = rememberScrollFloatingHeaderHidden(
        scrollState = scrollState,
        expandedHeaderSlack = expandedHeaderSlack,
        density = density,
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
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

fun LazyListState.headerCollapseFraction(): Float {
    val threshold = AppScreenDefaults.HeaderCollapseScrollThreshold
    return when {
        firstVisibleItemIndex > 0 -> 1f
        else -> (firstVisibleItemScrollOffset.toFloat() / threshold).coerceIn(0f, 1f)
    }
}

private const val FloatingHeaderHideHysteresisPx = 32f

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
                if (scrollPx <= slackPx - FloatingHeaderHideHysteresisPx) {
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
): Boolean {
    val collapseFraction by remember(scrollState) {
        derivedStateOf { scrollState.headerCollapseFraction() }
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
): Boolean {
    val collapseFraction by remember(lazyListState) {
        derivedStateOf { lazyListState.headerCollapseFraction() }
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

private fun androidx.compose.foundation.ScrollState.headerCollapseFraction(): Float {
    val threshold = AppScreenDefaults.HeaderCollapseScrollThreshold
    return (value.toFloat() / threshold).coerceIn(0f, 1f)
}

@Composable
private fun BoxScope.FloatingHeaderOverlay(
    hidden: Boolean,
    horizontalPadding: Dp,
    headerMeasureModifier: Modifier,
    headerContent: @Composable () -> Unit,
) {
    if (hidden) return
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .zIndex(1f)
            .fillMaxWidth()
            .floatingHeaderStatusBarPadding()
            .padding(start = horizontalPadding, end = horizontalPadding),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
    val statusBarTop = rememberStatusBarTopPadding()
    val bottomChrome = rememberBottomChromePadding()
    val collapseFraction by remember(scrollState) {
        derivedStateOf { scrollState.headerCollapseFraction() }
    }
    val (topContentPadding, headerMeasureModifier) = rememberFloatingHeaderTopPadding(
        collapseFraction = collapseFraction,
        statusBarTop = statusBarTop,
    )
    val compactHeaderClearance = rememberCompactFloatingHeaderClearance(statusBarTop)
    val expandedHeaderSlack = remember(topContentPadding, compactHeaderClearance) {
        (topContentPadding - compactHeaderClearance).coerceAtLeast(0.dp)
    }
    val headerHidden = rememberScrollFloatingHeaderHidden(
        scrollState = scrollState,
        expandedHeaderSlack = expandedHeaderSlack,
        density = density,
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
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
 * Root-level tether compass toast — mount once from [compose.project.click.click.App] so pings
 * surface on any tab, not only inside an open chat.
 */
@Composable
fun GlobalTetherOverlay(
    modifier: Modifier = Modifier,
) {
    val payload by EncounterTetherManager.activeTetherPayload.collectAsState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(payload) {
        toastMessage = payload?.let { ping ->
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
        modifier = modifier
            .padding(top = statusBarTop + 64.dp),
        onDismissed = { EncounterTetherManager.clearActiveTetherPayload() },
    )
}
