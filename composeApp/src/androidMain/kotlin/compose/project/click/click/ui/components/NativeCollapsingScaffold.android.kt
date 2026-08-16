@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
actual fun NativeCollapsingScaffold(
    title: String,
    modifier: Modifier,
    subtitle: String?,
    presenceOnline: Boolean?,
    navigationIcon: @Composable (() -> Unit)?,
    actions: @Composable (RowScope.() -> Unit)?,
    onOpenSearch: (() -> Unit)?,
    onNavigateBack: (() -> Unit)?,
    nativeTrailingActions: List<NativeChromeAction>,
    collapseSearchIntoBar: Boolean,
    showHeader: Boolean,
    belowHeaderSpacing: Dp,
    horizontalPadding: Dp,
    lazyListState: LazyListState,
    headerBelowContent: @Composable (() -> Unit)?,
    verticalArrangement: Arrangement.Vertical,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val bottomChrome = rememberBottomChromePadding()
    val statusBarTop = rememberStatusBarTopPadding()
    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .then(
                    if (showHeader) {
                        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        Modifier
                    },
                ),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showHeader) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LargeTopAppBar(
                        title = {
                            NativeCollapsingTitle(
                                title = title,
                                subtitle = subtitle,
                                presenceOnline = presenceOnline,
                                collapsedFraction = scrollBehavior.state.collapsedFraction,
                            )
                        },
                        navigationIcon = { navigationIcon?.invoke() },
                        actions = {
                            if (collapseSearchIntoBar &&
                                onOpenSearch != null &&
                                scrollBehavior.state.collapsedFraction > 0.32f
                            ) {
                                HeaderSearchIconButton(onClick = onOpenSearch)
                            }
                            actions?.invoke(this)
                        },
                        scrollBehavior = scrollBehavior,
                        colors =
                            TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor =
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            ),
                    )
                    headerBelowContent?.invoke()
                }
            }
        },
    ) { innerPadding ->
        val topPad =
            if (showHeader) {
                innerPadding.calculateTopPadding() + belowHeaderSpacing
            } else {
                statusBarTop + 16.dp
            }
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = verticalArrangement,
            contentPadding =
                PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = topPad,
                    bottom = bottomChrome,
                ),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
actual fun NativeCollapsingScrollScaffold(
    title: String,
    modifier: Modifier,
    subtitle: String?,
    presenceOnline: Boolean?,
    navigationIcon: @Composable (() -> Unit)?,
    actions: @Composable (RowScope.() -> Unit)?,
    onOpenSearch: (() -> Unit)?,
    onNavigateBack: (() -> Unit)?,
    nativeTrailingActions: List<NativeChromeAction>,
    horizontalPadding: Dp,
    content: @Composable (Modifier) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollState = rememberScrollState()
    val bottomChrome = rememberBottomChromePadding()
    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    NativeCollapsingTitle(
                        title = title,
                        subtitle = subtitle,
                        presenceOnline = presenceOnline,
                        collapsedFraction = scrollBehavior.state.collapsedFraction,
                    )
                },
                navigationIcon = { navigationIcon?.invoke() },
                actions = { actions?.invoke(this) },
                scrollBehavior = scrollBehavior,
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor =
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = innerPadding.calculateTopPadding(),
                        bottom = bottomChrome,
                    ),
        ) {
            content(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NativeCollapsingTitle(
    title: String,
    subtitle: String?,
    presenceOnline: Boolean?,
    collapsedFraction: Float,
) {
    Column {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
        )
        if (collapsedFraction < 0.55f && (!subtitle.isNullOrBlank() || presenceOnline != null)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (presenceOnline == true) {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E)),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
actual fun HidePlatformNativeNavigationBar() = Unit

@Composable
@Suppress("UNUSED_PARAMETER")
actual fun PlatformNativeNavigationBarSwipeReveal(revealPx: androidx.compose.runtime.MutableFloatState) = Unit

@Composable
@Suppress("UNUSED_PARAMETER")
actual fun BindPlatformNativeNavigationBar(
    title: String,
    subtitle: String?,
    presenceOnline: Boolean?,
    onNavigateBack: (() -> Unit)?,
    onOpenSearch: (() -> Unit)?,
    nativeTrailingActions: List<NativeChromeAction>,
    collapseFraction: Float,
) = Unit

@Composable
@Suppress("UNUSED_PARAMETER")
actual fun PlatformNativeMapFloatingChrome(
    visible: Boolean,
    layerLabel: String,
    layerOptions: List<NativeMapLayerOption>,
    onToggleLayerId: (String) -> Unit,
    onDropBeacon: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    bottomPadding: Dp,
) = Unit
