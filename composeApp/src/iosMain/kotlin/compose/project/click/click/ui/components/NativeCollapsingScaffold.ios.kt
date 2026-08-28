@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.platform.rememberReduceTransparencyEnabled // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalIsDarkMode // pragma: allowlist secret
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIImage

@OptIn(ExperimentalForeignApi::class, ExperimentalMaterial3Api::class)
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
    val bottomChrome = rememberBottomChromePadding()
    val statusBarTop = rememberStatusBarTopPadding()
    val hasSubtitle = !subtitle.isNullOrBlank() || presenceOnline == true
    val extraHeight = NativeHeaderMetrics.collapseRangeDp(hasSubtitle)
    val density = LocalDensity.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val extraPx = with(density) { extraHeight.toPx() }
    SideEffect {
        if (scrollBehavior.state.heightOffsetLimit != -extraPx) {
            scrollBehavior.state.heightOffsetLimit = -extraPx
        }
    }
    val collapseFraction =
        when {
            !showHeader -> 0f
            onNavigateBack != null -> 1f
            else -> scrollBehavior.state.collapsedFraction
        }
    val chromeActive = LocalNativeChromeActive.current
    rememberIosHostNavBar(
        title = title,
        subtitle = subtitle,
        presenceOnline = presenceOnline,
        collapseFraction = collapseFraction,
        visible = showHeader && chromeActive,
        overlay = onNavigateBack != null,
        onOpenSearch = onOpenSearch,
        onNavigateBack = onNavigateBack,
        nativeTrailingActions = nativeTrailingActions,
        collapseSearchIntoBar = collapseSearchIntoBar,
    )

    val headerClearance =
        if (showHeader) {
            NativeHeaderMetrics.headerClearanceDp(
                statusBarTop = statusBarTop,
                collapseFraction = collapseFraction,
                hasSubtitle = hasSubtitle,
                growCompactSubtitle =
                    NativeHeaderMetrics.shouldGrowCompactBarForStackedSubtitle(
                        hasBack = onNavigateBack != null,
                        hasIdentity = false,
                        hasSubtitle = hasSubtitle,
                        collapseFraction = collapseFraction,
                    ),
            )
        } else {
            statusBarTop + 16.dp
        }

    Box(
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
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = verticalArrangement,
            contentPadding =
                PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = headerClearance + belowHeaderSpacing,
                    bottom = bottomChrome,
                ),
            content = content,
        )
        if (showHeader) {
            if (!chromeActive) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.fillMaxWidth().height(headerClearance))
                    headerBelowContent?.invoke()
                }
            } else if (headerBelowContent != null) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = headerClearance),
                ) {
                    headerBelowContent.invoke()
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalMaterial3Api::class)
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
    val scrollState = rememberScrollState()
    val bottomChrome = rememberBottomChromePadding()
    val statusBarTop = rememberStatusBarTopPadding()
    val hasSubtitle = !subtitle.isNullOrBlank() || presenceOnline == true
    val extraHeight = NativeHeaderMetrics.collapseRangeDp(hasSubtitle)
    val density = LocalDensity.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val extraPx = with(density) { extraHeight.toPx() }
    SideEffect {
        if (scrollBehavior.state.heightOffsetLimit != -extraPx) {
            scrollBehavior.state.heightOffsetLimit = -extraPx
        }
    }
    val collapseFraction = if (onNavigateBack != null) 1f else scrollBehavior.state.collapsedFraction
    val chromeActive = LocalNativeChromeActive.current
    rememberIosHostNavBar(
        title = title,
        subtitle = subtitle,
        presenceOnline = presenceOnline,
        collapseFraction = collapseFraction,
        visible = chromeActive,
        overlay = onNavigateBack != null,
        onOpenSearch = onOpenSearch,
        onNavigateBack = onNavigateBack,
        nativeTrailingActions = nativeTrailingActions,
        collapseSearchIntoBar = false,
    )

    val headerClearance =
        NativeHeaderMetrics.headerClearanceDp(
            statusBarTop = statusBarTop,
            collapseFraction = collapseFraction,
            hasSubtitle = hasSubtitle,
            growCompactSubtitle =
                NativeHeaderMetrics.shouldGrowCompactBarForStackedSubtitle(
                    hasBack = onNavigateBack != null,
                    hasIdentity = false,
                    hasSubtitle = hasSubtitle,
                    collapseFraction = collapseFraction,
                ),
        )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = headerClearance,
                        bottom = bottomChrome,
                    ),
        ) {
            content(Modifier.fillMaxWidth())
        }
        if (!chromeActive) {
            Spacer(Modifier.fillMaxWidth().height(headerClearance))
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalForeignApi::class)
@Composable
private fun rememberIosHostNavBar(
    title: String,
    subtitle: String?,
    presenceOnline: Boolean?,
    collapseFraction: Float,
    visible: Boolean,
    overlay: Boolean,
    onOpenSearch: (() -> Unit)?,
    onNavigateBack: (() -> Unit)?,
    nativeTrailingActions: List<NativeChromeAction>,
    collapseSearchIntoBar: Boolean,
    identity: NativeChromeIdentity? = null,
    leadingClose: Boolean = false,
) {
    val viewController = LocalUIViewController.current
    val isDarkMode = LocalIsDarkMode.current
    val reduceTransparency = rememberReduceTransparencyEnabled()
    val usesNativeLiquidGlass =
        remember {
            NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }
        }
    val owner = remember { Any() }
    val searchHandler by rememberUpdatedState(onOpenSearch)
    val backHandler by rememberUpdatedState(onNavigateBack)
    val trailingHandlers by rememberUpdatedState(nativeTrailingActions)
    val identityHandler by rememberUpdatedState(identity)
    val hasSubtitle = !subtitle.isNullOrBlank() || presenceOnline != null
    val layer = if (overlay) IosNavChrome.overlay else IosNavChrome.tab
    // Snapshot read so chat rebinds when Click Drops clears exclusive ownership.
    val exclusiveOwner = if (overlay) IosNavChrome.overlayExclusiveOwner else null

    DisposableEffect(viewController, overlay, leadingClose) {
        layer.attach(viewController)
        if (overlay) {
            IosNavChrome.registerOverlayBinder(owner)
        }
        if (overlay && leadingClose) {
            IosNavChrome.overlayExclusiveOwner = owner
        }
        onDispose {
            val wasExclusive = IosNavChrome.overlayExclusiveOwner === owner
            val othersRemain = overlay && IosNavChrome.otherOverlayBindersRemain(except = owner)
            if (wasExclusive) {
                IosNavChrome.overlayExclusiveOwner = null
            }
            if (overlay) {
                IosNavChrome.unregisterOverlayBinder(owner)
            }
            val hideExclusive =
                overlay &&
                    leadingClose &&
                    OverlayExclusiveBindPolicy.shouldHideOverlayOnExclusiveRelease(othersRemain)
            if (overlay && leadingClose && !hideExclusive) {
                layer.yieldExclusive(owner)
            } else {
                layer.release(owner, immediate = overlay)
            }
        }
    }

    SideEffect {
        if (overlay && OverlayExclusiveBindPolicy.shouldSkipOverlayBind(exclusiveOwner, owner)) {
            return@SideEffect
        }
        if (!visible) {
            // Inactive underlays (Map swipe-back composing Home) must not unhide stale
            // tab chrome. Only hide if this composition currently owns the layer.
            if (layer.owns(owner)) {
                layer.setWantVisible(false)
            }
            return@SideEffect
        }
        layer.applyAppearance(
            isDarkMode = isDarkMode,
            reduceTransparency = reduceTransparency,
            usesNativeLiquidGlass = usesNativeLiquidGlass,
        )
        layer.update(
            owner = owner,
            host = viewController,
            title = title,
            subtitle = subtitle,
            presenceOnline = presenceOnline,
            identity = identityHandler,
            visible = true,
            collapseFraction = collapseFraction,
            hasSubtitle = hasSubtitle,
            onOpenSearch = searchHandler,
            onNavigateBack = backHandler,
            trailingActions = trailingHandlers,
            collapseSearchIntoBar = collapseSearchIntoBar,
            leadingClose = leadingClose,
        )
    }
}

@Composable
actual fun HidePlatformNativeNavigationBar() {
    DisposableEffect(Unit) {
        IosNavChrome.acquireCover()
        onDispose { IosNavChrome.releaseCover() }
    }
}

@Composable
actual fun CoverPlatformOverlayNavigationBar() {
    DisposableEffect(Unit) {
        IosNavChrome.overlay.acquireCover()
        onDispose { IosNavChrome.overlay.releaseCover() }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformNativeNavigationBarSwipeReveal(revealPx: MutableFloatState) {
    val owner = remember { Any() }
    val density = LocalDensity.current.density
    DisposableEffect(owner) {
        onDispose { IosNavChrome.overlay.setSlideOffset(owner, 0.0) }
    }
    LaunchedEffect(owner, density) {
        snapshotFlow { revealPx.floatValue }.collect { px ->
            IosNavChrome.overlay.setSlideOffset(owner, (px / density).toDouble())
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun BindPlatformNativeNavigationBar(
    title: String,
    subtitle: String?,
    presenceOnline: Boolean?,
    identity: NativeChromeIdentity?,
    onNavigateBack: (() -> Unit)?,
    onOpenSearch: (() -> Unit)?,
    nativeTrailingActions: List<NativeChromeAction>,
    collapseFraction: Float,
    leadingClose: Boolean,
) {
    rememberIosHostNavBar(
        title = title,
        subtitle = subtitle,
        presenceOnline = presenceOnline,
        collapseFraction = collapseFraction,
        visible = LocalNativeChromeActive.current,
        overlay = true,
        onOpenSearch = onOpenSearch,
        onNavigateBack = onNavigateBack,
        nativeTrailingActions = nativeTrailingActions,
        collapseSearchIntoBar = false,
        identity = identity,
        leadingClose = leadingClose,
    )
}

internal object IosNavChrome {
    val tab = IosHostNavBarLayer()
    val overlay = IosHostNavBarLayer()
    val avatarPhotos = mutableMapOf<String, UIImage>()

    /**
     * Camera / sheet that rebinds overlay chrome while [overlay] coverCount > 0.
     * Snapshot state so the underlying conversation bind recomposes and restores titles
     * when exclusive ownership clears.
     */
    var overlayExclusiveOwner by mutableStateOf<Any?>(null)
    private val overlayBindOwners = mutableSetOf<Any>()

    fun registerOverlayBinder(owner: Any) {
        overlayBindOwners.add(owner)
    }

    fun unregisterOverlayBinder(owner: Any) {
        overlayBindOwners.remove(owner)
    }

    fun otherOverlayBindersRemain(except: Any): Boolean = overlayBindOwners.any { it !== except }

    fun acquireCover() {
        tab.acquireCover()
        overlay.acquireCover()
    }

    fun releaseCover() {
        tab.releaseCover()
        overlay.releaseCover()
    }

    fun restack() {
        tab.bringChromeToFront()
        overlay.bringChromeToFront()
    }
}
