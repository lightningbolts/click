@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.platform.rememberReduceMotionEnabled // pragma: allowlist secret
import compose.project.click.click.platform.rememberReduceTransparencyEnabled // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalIsDarkMode // pragma: allowlist secret
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.launch
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIImage
import platform.UIKit.UIViewController

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
    val reduceMotion = rememberReduceMotionEnabled()
    val usesNativeLiquidGlass =
        remember {
            NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }
        }
    val owner = remember { Any() }
    val searchHandler by rememberUpdatedState(onOpenSearch)
    val backHandler by rememberUpdatedState(onNavigateBack)
    val trailingHandlers by rememberUpdatedState(nativeTrailingActions)
    val identityHandler by rememberUpdatedState(identity)
    val morphScope = rememberCoroutineScope()
    val tapMorph = remember(owner) { Animatable(0f) }
    val hasSubtitle = !subtitle.isNullOrBlank() || presenceOnline != null
    val level =
        when {
            leadingClose -> NativeChromeLevel.EXCLUSIVE
            overlay -> NativeChromeLevel.OVERLAY
            else -> NativeChromeLevel.ROOT
        }

    val coordinatedBackHandler: (() -> Unit)? =
        backHandler?.let { destinationBack ->
            {
                if (overlay && !leadingClose && !reduceMotion) {
                    IosNavChrome.beginProgrammaticPop(owner)
                    morphScope.launch {
                        tapMorph.stop()
                        tapMorph.snapTo(0f)
                        tapMorph.animateTo(
                            targetValue = 1f,
                            animationSpec =
                                tween(
                                    durationMillis = NATIVE_ROUTE_HEADER_TRANSITION_MS,
                                    easing = FastOutSlowInEasing,
                                ),
                        ) {
                            IosNavChrome.setProgrammaticPopProgress(owner, value)
                        }
                        IosNavChrome.endProgrammaticPop(owner)
                    }
                }
                destinationBack()
            }
        }

    DisposableEffect(owner, viewController) {
        IosNavChrome.register(owner, level)
        onDispose {
            IosNavChrome.unregister(owner)
        }
    }

    SideEffect {
        IosNavChrome.publish(
            PublishedNativeChromeState(
                owner = owner,
                host = viewController,
                level = level,
                title = title,
                subtitle = subtitle,
                presenceOnline = presenceOnline,
                identity = identityHandler,
                visible = visible,
                collapseFraction = collapseFraction,
                hasSubtitle = hasSubtitle,
                onOpenSearch = searchHandler,
                onNavigateBack = coordinatedBackHandler,
                trailingActions = trailingHandlers,
                collapseSearchIntoBar = collapseSearchIntoBar,
                leadingClose = leadingClose,
                isDarkMode = isDarkMode,
                reduceTransparency = reduceTransparency,
                reduceMotion = reduceMotion,
                usesNativeLiquidGlass = usesNativeLiquidGlass,
            ),
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

/**
 * The global persistent host no longer needs to hide one native bar before mounting another.
 * Keeping this API as a no-op preserves camera/sheet call sites while preventing Liquid Glass
 * rematerialization during exclusive overlay transitions.
 */
@Composable
actual fun CoverPlatformOverlayNavigationBar() = Unit

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformNativeNavigationBarSwipeReveal(revealPx: MutableFloatState) {
    val owner = remember { Any() }
    val density = LocalDensity.current.density
    DisposableEffect(owner) {
        onDispose { IosNavChrome.setInteractivePopOffset(owner, 0.0) }
    }
    LaunchedEffect(owner, density) {
        snapshotFlow { revealPx.floatValue }.collect { px ->
            IosNavChrome.setInteractivePopOffset(owner, (px / density).toDouble())
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

@Composable
actual fun ApplyOverlayMediaChrome(
    active: Boolean,
    onClose: () -> Unit,
    trailing: List<NativeChromeAction>,
) {
    val close by rememberUpdatedState(onClose)
    val trailingLatest by rememberUpdatedState(trailing)
    val trailingShape =
        trailing.map { action ->
            Triple(
                action.sfSymbol,
                action.contentDescription,
                action.menuItems.map { item -> item.title to item.sfSymbol },
            )
        }
    val stableTrailing =
        remember(trailingShape) {
            trailing.mapIndexed { actionIndex, action ->
                action.copy(
                    onClick = { trailingLatest.getOrNull(actionIndex)?.onClick?.invoke() },
                    menuItems =
                        action.menuItems.mapIndexed { itemIndex, item ->
                            item.copy(
                                onClick = {
                                    trailingLatest
                                        .getOrNull(actionIndex)
                                        ?.menuItems
                                        ?.getOrNull(itemIndex)
                                        ?.onClick
                                        ?.invoke()
                                },
                            )
                        },
                )
            }
        }
    val stableMediaChrome =
        remember(stableTrailing) {
            OverlayMediaChrome(
                onClose = {
                    IosNavChrome.setMediaChrome(null)
                    close()
                },
                trailing = stableTrailing,
            )
        }
    val hasUnderlyingBinder = IosNavChrome.hasOverlayBinder()

    DisposableEffect(active, hasUnderlyingBinder, stableMediaChrome) {
        if (active && hasUnderlyingBinder) {
            IosNavChrome.setMediaChrome(stableMediaChrome)
        } else if (!active) {
            IosNavChrome.setMediaChrome(null)
        }
        onDispose {
            IosNavChrome.clearMediaChromeIf(stableMediaChrome)
        }
    }

    if (active && !hasUnderlyingBinder) {
        rememberIosHostNavBar(
            title = "",
            subtitle = null,
            presenceOnline = null,
            collapseFraction = 1f,
            visible = LocalNativeChromeActive.current,
            overlay = true,
            onOpenSearch = null,
            onNavigateBack = { stableMediaChrome.onClose() },
            nativeTrailingActions = stableTrailing,
            collapseSearchIntoBar = false,
            leadingClose = true,
        )
    }
}

internal data class OverlayMediaChrome(
    val onClose: () -> Unit,
    val trailing: List<NativeChromeAction>,
)

internal enum class NativeChromeLevel(
    val priority: Int,
) {
    ROOT(0),
    OVERLAY(1),
    EXCLUSIVE(2),
}

@OptIn(ExperimentalForeignApi::class)
internal data class PublishedNativeChromeState(
    val owner: Any,
    val host: UIViewController,
    val level: NativeChromeLevel,
    val title: String,
    val subtitle: String?,
    val presenceOnline: Boolean?,
    val identity: NativeChromeIdentity?,
    val visible: Boolean,
    val collapseFraction: Float,
    val hasSubtitle: Boolean,
    val onOpenSearch: (() -> Unit)?,
    val onNavigateBack: (() -> Unit)?,
    val trailingActions: List<NativeChromeAction>,
    val collapseSearchIntoBar: Boolean,
    val leadingClose: Boolean,
    val isDarkMode: Boolean,
    val reduceTransparency: Boolean,
    val reduceMotion: Boolean,
    val usesNativeLiquidGlass: Boolean,
    val mountOrder: Long = 0L,
)

/**
 * Single-owner iOS chrome coordinator.
 *
 * Root, pushed, hub, media, and camera states all publish into this coordinator. There is exactly
 * one attached [IosHostNavBarLayer], so the leading/trailing UIViews stay physically mounted at
 * identical coordinates while their semantic role changes. No route owns a second navigation bar.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosNavChrome {
    val shared = IosHostNavBarLayer()

    // Kept only because legacy layer helpers refer to these identities internally. New app chrome
    // never attaches either instance; [shared] is the only live host.
    val tab = IosHostNavBarLayer()
    val overlay = IosHostNavBarLayer()
    val avatarPhotos = mutableMapOf<String, UIImage>()

    var overlayExclusiveOwner: Any? = null

    private val states = linkedMapOf<Any, PublishedNativeChromeState>()
    private val mountOrders = mutableMapOf<Any, Long>()
    private var nextMountOrder = 1L
    private var coverCount = 0
    private var mediaChrome: OverlayMediaChrome? = null
    private var lastSemanticSignature: String? = null
    private var lastRenderedOwner: Any? = null

    private val interactiveOffsetsPt = mutableMapOf<Any, Double>()
    private var interactiveSourceOwner: Any? = null
    private var programmaticSourceOwner: Any? = null
    private var programmaticProgress = 0f

    fun register(
        owner: Any,
        level: NativeChromeLevel,
    ) {
        if (owner !in mountOrders) {
            mountOrders[owner] = nextMountOrder++
        }
        if (level == NativeChromeLevel.EXCLUSIVE) {
            overlayExclusiveOwner = owner
        }
    }

    fun unregister(owner: Any) {
        states.remove(owner)
        mountOrders.remove(owner)
        if (overlayExclusiveOwner === owner) {
            overlayExclusiveOwner = null
        }
        if (interactiveSourceOwner === owner) {
            interactiveSourceOwner = null
            interactiveOffsetsPt.clear()
        }
        if (programmaticSourceOwner === owner) {
            programmaticSourceOwner = null
            programmaticProgress = 0f
        }
        reconcile()
    }

    fun publish(state: PublishedNativeChromeState) {
        val order = mountOrders[state.owner] ?: nextMountOrder++.also { mountOrders[state.owner] = it }
        states[state.owner] = state.copy(mountOrder = order)
        reconcile()
    }

    fun hasOverlayBinder(): Boolean =
        states.values.any {
            it.visible && (it.level == NativeChromeLevel.OVERLAY || it.level == NativeChromeLevel.EXCLUSIVE)
        }

    fun setMediaChrome(value: OverlayMediaChrome?) {
        if (mediaChrome === value) return
        mediaChrome = value
        reconcile()
    }

    fun clearMediaChromeIf(value: OverlayMediaChrome) {
        if (mediaChrome === value) {
            mediaChrome = null
            reconcile()
        }
    }

    fun acquireCover() {
        coverCount += 1
        shared.acquireCover()
    }

    fun releaseCover() {
        coverCount = (coverCount - 1).coerceAtLeast(0)
        shared.releaseCover()
        reconcile()
    }

    fun restack() {
        shared.bringChromeToFront()
    }

    fun beginProgrammaticPop(owner: Any) {
        if (states[owner]?.visible != true) return
        programmaticSourceOwner = owner
        programmaticProgress = 0f
        renderTransition(owner, 0f)
    }

    fun setProgrammaticPopProgress(
        owner: Any,
        progress: Float,
    ) {
        if (programmaticSourceOwner !== owner) return
        programmaticProgress = progress.coerceIn(0f, 1f)
        renderTransition(owner, programmaticProgress)
    }

    fun endProgrammaticPop(owner: Any) {
        if (programmaticSourceOwner !== owner) return
        programmaticSourceOwner = null
        programmaticProgress = 0f
        shared.resetPersistentChromeMorphVisuals(animated = false)
        reconcile()
    }

    fun setInteractivePopOffset(
        gestureOwner: Any,
        offsetPt: Double,
    ) {
        if (offsetPt <= 0.5) {
            interactiveOffsetsPt.remove(gestureOwner)
        } else {
            interactiveOffsetsPt[gestureOwner] = offsetPt
        }

        val maxOffset = interactiveOffsetsPt.values.maxOrNull() ?: 0.0
        if (maxOffset <= 0.5) {
            interactiveSourceOwner = null
            shared.resetPersistentChromeMorphVisuals(animated = true)
            reconcile()
            return
        }

        if (interactiveSourceOwner == null) {
            interactiveSourceOwner = activeState()?.owner
        }
        val sourceOwner = interactiveSourceOwner ?: return
        val source =
            states[sourceOwner] ?: run {
                interactiveSourceOwner = activeState()?.owner
                return
            }
        val width =
            source.host.view.bounds
                .useContents { size.width }
                .coerceAtLeast(1.0)
        val progress = (maxOffset / width).toFloat().coerceIn(0f, 1f)
        renderTransition(sourceOwner, progress)
    }

    private fun activeState(excludingOwner: Any? = null): PublishedNativeChromeState? =
        states.values
            .asSequence()
            .filter { it.visible && it.owner !== excludingOwner }
            .maxWithOrNull(
                compareBy<PublishedNativeChromeState> { it.level.priority }
                    .thenBy { it.mountOrder },
            )

    private fun renderTransition(
        sourceOwner: Any,
        progress: Float,
    ) {
        val source = states[sourceOwner] ?: activeState() ?: return
        val destination = activeState(excludingOwner = sourceOwner)
        val p = progress.coerceIn(0f, 1f)
        val state = if (destination != null && p >= 0.5f) destination else source
        render(state, animateSemanticChange = false)
        shared.applyPersistentChromeMorphProgress(p)
    }

    private fun reconcile() {
        if (coverCount > 0) return
        val programmaticOwner = programmaticSourceOwner
        if (programmaticOwner != null && states[programmaticOwner]?.visible == true) {
            renderTransition(programmaticOwner, programmaticProgress)
            return
        }
        val interactiveOwner = interactiveSourceOwner
        if (interactiveOwner != null && interactiveOffsetsPt.isNotEmpty()) {
            val source = states[interactiveOwner]
            if (source != null) {
                val width =
                    source.host.view.bounds
                        .useContents { size.width }
                        .coerceAtLeast(1.0)
                val offset = interactiveOffsetsPt.values.maxOrNull() ?: 0.0
                renderTransition(interactiveOwner, (offset / width).toFloat())
                return
            }
        }
        val active = activeState()
        if (active == null) {
            shared.setWantVisible(false)
            return
        }
        render(active, animateSemanticChange = true)
    }

    private fun render(
        state: PublishedNativeChromeState,
        animateSemanticChange: Boolean,
    ) {
        val overlayLike = state.level != NativeChromeLevel.ROOT
        val effectiveMedia = mediaChrome.takeIf { overlayLike && !state.leadingClose }
        val effectiveBack = effectiveMedia?.onClose ?: state.onNavigateBack
        val effectiveTrailing = effectiveMedia?.trailing ?: state.trailingActions
        val effectiveClose = state.leadingClose || effectiveMedia != null

        shared.attach(state.host)
        shared.applyAppearance(
            isDarkMode = state.isDarkMode,
            reduceTransparency = state.reduceTransparency,
            usesNativeLiquidGlass = state.usesNativeLiquidGlass,
        )
        shared.update(
            owner = state.owner,
            host = state.host,
            title = state.title,
            subtitle = state.subtitle,
            presenceOnline = state.presenceOnline,
            identity = state.identity,
            visible = state.visible,
            collapseFraction = state.collapseFraction,
            hasSubtitle = state.hasSubtitle,
            onOpenSearch = state.onOpenSearch,
            onNavigateBack = effectiveBack,
            trailingActions = effectiveTrailing,
            collapseSearchIntoBar = state.collapseSearchIntoBar,
            leadingClose = effectiveClose,
        )

        val isRoot = state.level == NativeChromeLevel.ROOT
        if (isRoot) {
            shared.applyPersistentRootTitleGeometry(isRoot = true)
            val rootMenuHandler =
                state.onOpenSearch
                    ?: state.trailingActions.firstOrNull()?.onClick
                    ?: {}
            shared.applyPersistentRootMenu(rootMenuHandler)
        }

        if (overlayLike) {
            IosHostMapFloatingChrome.clipLeadingUnderlay(0.0)
        } else {
            IosHostMapFloatingChrome.clipLeadingUnderlay(null)
        }

        val leadingSymbol =
            when {
                effectiveClose -> "xmark"
                isRoot -> "ellipsis"
                effectiveBack != null -> "chevron.backward"
                else -> "none"
            }
        val semanticSignature =
            buildString {
                append(leadingSymbol)
                effectiveTrailing.forEach {
                    append('|')
                    append(it.sfSymbol)
                }
            }
        val semanticChanged = lastSemanticSignature != null && lastSemanticSignature != semanticSignature
        lastSemanticSignature = semanticSignature
        lastRenderedOwner = state.owner
        shared.animatePersistentSemanticSettle(
            enabled = animateSemanticChange && semanticChanged && !state.reduceMotion,
        )
        shared.bringChromeToFront()
    }
}

private const val NATIVE_ROUTE_HEADER_TRANSITION_MS = 300