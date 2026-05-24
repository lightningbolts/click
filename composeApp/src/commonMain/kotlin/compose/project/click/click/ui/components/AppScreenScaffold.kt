package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

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
    horizontalPadding: Dp = AppScreenDefaults.HorizontalPadding,
    lazyListState: LazyListState = rememberLazyListState(),
    headerBelowContent: @Composable (() -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomChrome = rememberBottomChromePadding()
    val collapseFraction by remember(lazyListState) {
        derivedStateOf { lazyListState.headerCollapseFraction() }
    }
    val headerHeight = remember(collapseFraction, statusBarTop) {
        val collapsed = AppScreenDefaults.FloatingHeaderCompactHeight
        val expanded = AppScreenDefaults.FloatingHeaderLargeHeight
        statusBarTop + collapsed + (expanded - collapsed) * (1f - collapseFraction)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = verticalArrangement,
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = headerHeight + AppScreenDefaults.SectionSpacing,
                bottom = bottomChrome,
            ),
            content = content,
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f)
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = statusBarTop,
                ),
        ) {
            LiquidGlassPageHeader(
                title = title,
                subtitle = subtitle,
                presenceOnline = presenceOnline,
                navigationIcon = navigationIcon,
                actions = actions,
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
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomChrome = rememberBottomChromePadding()
    val collapseFraction by remember(scrollState) {
        derivedStateOf { scrollState.headerCollapseFraction() }
    }
    val headerHeight = remember(collapseFraction, statusBarTop) {
        val collapsed = AppScreenDefaults.FloatingHeaderCompactHeight
        val expanded = AppScreenDefaults.FloatingHeaderLargeHeight
        statusBarTop + collapsed + (expanded - collapsed) * (1f - collapseFraction)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = headerHeight + AppScreenDefaults.SectionSpacing,
                    bottom = bottomChrome,
                ),
        ) {
            content(Modifier)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f)
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = statusBarTop,
                ),
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

private fun androidx.compose.foundation.ScrollState.headerCollapseFraction(): Float {
    val threshold = AppScreenDefaults.HeaderCollapseScrollThreshold
    return (value.toFloat() / threshold).coerceIn(0f, 1f)
}

/**
 * Non-scroll screens (e.g. Add Click) with a floating header and bottom chrome insets.
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
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomChrome = rememberBottomChromePadding()
    val topContentPadding =
        statusBarTop + AppScreenDefaults.FloatingHeaderLargeHeight + AppScreenDefaults.SectionSpacing

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = topContentPadding,
                    bottom = bottomChrome,
                ),
        ) {
            content(Modifier.fillMaxSize())
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f)
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = statusBarTop,
                ),
        ) {
            LiquidGlassPageHeader(
                title = title,
                subtitle = subtitle,
                presenceOnline = presenceOnline,
                navigationIcon = navigationIcon,
                actions = actions,
                collapseFraction = 0f,
            )
        }
    }
}
