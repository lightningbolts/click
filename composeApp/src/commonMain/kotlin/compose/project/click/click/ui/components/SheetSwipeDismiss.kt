package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import compose.project.click.click.platform.rememberKeyboardHeightProvider
import compose.project.click.click.ui.theme.LocalPlatformStyle

val LocalSheetScrollAtTop = compositionLocalOf { { true } }

/**
 * Non-snapshot holder so nested [ProvideSheetSwipeDismiss] can update scroll-at-top
 * without writing Compose [MutableState] (which crashed profile scroll via Snapshot.valid).
 */
class SheetScrollAtTopHolder(
    initial: () -> Boolean = { true },
) {
    private var check: () -> Boolean = initial
    fun get(): Boolean = check()
    fun set(next: () -> Boolean) {
        check = next
    }
}

val LocalSheetScrollAtTopHolder =
    compositionLocalOf<SheetScrollAtTopHolder?> { null }

val LocalSheetOnDismissRequest = compositionLocalOf { {} }

/**
 * When true, [ClickSheetDialogChrome] hides its Compose grabber because the host draws one
 * (UIKit system grabber / Material dragHandle).
 */
val LocalSheetUsesPlatformGrabber = compositionLocalOf { false }

/**
 * When true (iOS UIKit [UIScrollView] host), Compose must not use [verticalScroll] —
 * the host owns scroll-edge expand and swipe-to-dismiss. Use [modifier.sheetBodyScroll].
 */
val LocalSheetScrollOwnedByHost = compositionLocalOf { false }

/**
 * When true, UIKit host fills the sheet viewport (LazyColumn / pager). Nested lists steal
 * UIScrollView pans — Compose tracks pull distance and dismisses on release past threshold
 * (no live rubber-band / transform — those flicker against UISheetPresentationController).
 * Also allows Compose [sheetImePadding] (host keyboard contentInset is not applied).
 */
val LocalSheetUiKitFillViewport = compositionLocalOf { false }

/**
 * Non-null sentinel for fill-viewport sheets: enables threshold pull-dismiss tracking.
 * Live offset application is intentionally a no-op (see [SheetDismissGestureRefs.applyPullVisual]).
 */
val LocalSheetHostRubberBandPx = compositionLocalOf<((Float) -> Unit)?> { null }

/** Legacy surface-drag hook for Compose-fill sheets. Live offset is not applied on iOS. */
val LocalSheetSurfaceDragOffsetPx = compositionLocalOf<(Float) -> Unit> { {} }

val LocalSheetSurfaceDragActive = compositionLocalOf { false }

val LocalSheetFingerDismissInstalled = compositionLocalOf { false }

@Composable
fun ProvideSheetSwipeDismiss(
    onDismissRequest: () -> Unit,
    scrollAtTop: () -> Boolean = { true },
    content: @Composable () -> Unit,
) {
    val holder = LocalSheetScrollAtTopHolder.current
    DisposableEffect(scrollAtTop, holder) {
        holder?.set(scrollAtTop)
        onDispose { holder?.set { true } }
    }
    CompositionLocalProvider(
        LocalSheetOnDismissRequest provides onDismissRequest,
        LocalSheetScrollAtTop provides scrollAtTop,
        content = content,
    )
}

@Composable
fun ProvideSheetSurfaceDrag(
    onDragOffsetPx: (Float) -> Unit,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSheetSurfaceDragOffsetPx provides onDragOffsetPx,
        LocalSheetSurfaceDragActive provides true,
        content = content,
    )
}

fun ScrollState.isSheetScrollAtTop(): Boolean = value <= 0

fun LazyListState.isSheetScrollAtTop(): Boolean =
    firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset <= 0

/**
 * Vertical scroll for sheet bodies. No-op when [LocalSheetScrollOwnedByHost] is true.
 */
@Composable
fun Modifier.sheetBodyScroll(
    state: ScrollState = rememberScrollState(),
): Modifier {
    if (LocalSheetScrollOwnedByHost.current) return this
    return this.verticalScroll(state)
}

/**
 * IME inset for form sheets. `imePadding()` is unreliable inside iOS UIKit page sheets
 * (WindowInsets.ime stays 0) — use the native keyboard overlap there instead.
 *
 * Wrap-content UIKit hosts apply keyboard via UIScrollView contentInset (no Compose pad).
 * Fill-viewport UIKit hosts and Compose fill sheets use this padding so content clears IME.
 */
fun Modifier.sheetImePadding(): Modifier = composed {
    val hostOwned = LocalSheetScrollOwnedByHost.current
    val fillViewport = LocalSheetUiKitFillViewport.current
    if (hostOwned && !fillViewport) return@composed this

    val isIos = LocalPlatformStyle.current.isIOS
    if (!isIos) return@composed this.imePadding()

    val provider = rememberKeyboardHeightProvider()
    LaunchedEffect(provider) { provider.syncFromSystem() }
    val heightPoints by provider.keyboardHeight.collectAsState()
    this.padding(bottom = heightPoints.dp)
}

private val SheetFingerDismissThresholdDp = 88.dp

/**
 * Gesture refs updated only from composition ([SideEffect]), never from nested-scroll.
 * Nested-scroll must not read/write Compose snapshot state — that races layout apply
 * and crashes with Snapshot.valid (profile sheet scroll).
 */
private class SheetDismissGestureRefs {
    var onDismiss: () -> Unit = {}
    var scrollAtTopOverride: (() -> Boolean)? = null
    var holder: SheetScrollAtTopHolder? = null
    var localScrollAtTop: () -> Boolean = { true }
    var surfaceDrag: (Float) -> Unit = {}
    var surfaceDragActive: Boolean = false
    var hostRubberBand: ((Float) -> Unit)? = null
    var blockSurfaceDrag: Boolean = false

    fun interactiveDismissActive(): Boolean =
        surfaceDragActive || hostRubberBand != null

    /**
     * Live pull visuals fight UISheetPresentationController (flicker/stutter).
     * Keep architecture (fillViewport + sheetImePadding); dismiss only on release/fling.
     */
    fun applyPullVisual(@Suppress("UNUSED_PARAMETER") offsetPx: Float) {
        // no-op
    }
}

/**
 * - Android / Material: same-gesture gate only (leave leftovers for sheet).
 * - iOS wrap UIKit host: UIScrollView owns dismiss (no Compose pull).
 * - iOS fill-viewport / Compose-fill: track pull distance; commit [onDismissRequest] past
 *   threshold (no live rubber-band / surface transform — those flicker).
 *
 * Important: never write Compose snapshot state from nested-scroll callbacks — that races
 * scroll/layout applies and crashes with Snapshot.valid (seen on profile sheet scroll).
 */
@Composable
fun Modifier.sheetSwipeDismissWhenAtTop(
    onDismissRequest: () -> Unit = LocalSheetOnDismissRequest.current,
    scrollAtTop: (() -> Boolean)? = null,
): Modifier {
    if (LocalSheetFingerDismissInstalled.current) return this

    val density = LocalDensity.current
    val thresholdPx = with(density) { SheetFingerDismissThresholdDp.toPx() }
    val holder = LocalSheetScrollAtTopHolder.current
    val localScrollAtTop = LocalSheetScrollAtTop.current
    val surfaceDrag = LocalSheetSurfaceDragOffsetPx.current
    val surfaceDragActive = LocalSheetSurfaceDragActive.current
    val hostRubberBand = LocalSheetHostRubberBandPx.current
    val scrollOwnedByHost = LocalSheetScrollOwnedByHost.current
    val fillViewport = LocalSheetUiKitFillViewport.current
    val isIos = LocalPlatformStyle.current.isIOS
    val keyboardProvider = rememberKeyboardHeightProvider()
    val interactivePull = surfaceDragActive || hostRubberBand != null
    LaunchedEffect(isIos, interactivePull, keyboardProvider) {
        if (isIos && interactivePull) keyboardProvider.syncFromSystem()
    }
    val keyboardHeightPoints by keyboardProvider.keyboardHeight.collectAsState()
    // Keyboard open: do not steal nested scroll for dismiss (IME stays usable).
    val blockSurfaceDrag = isIos && interactivePull && keyboardHeightPoints > 0.5f
    // Wrap-content UIKit host: system UIScrollView dismiss — do not attach Compose pull.
    if (isIos && scrollOwnedByHost && !fillViewport) {
        return this
    }
    val refs = remember { SheetDismissGestureRefs() }
    SideEffect {
        refs.onDismiss = onDismissRequest
        refs.scrollAtTopOverride = scrollAtTop
        refs.holder = holder
        refs.localScrollAtTop = localScrollAtTop
        refs.surfaceDrag = surfaceDrag
        refs.surfaceDragActive = surfaceDragActive
        refs.hostRubberBand = hostRubberBand
        refs.blockSurfaceDrag = blockSurfaceDrag
    }

    val connection = remember(thresholdPx) {
        object : NestedScrollConnection {
            private var dragOffsetPx = 0f
            private var contentScrolledThisGesture = false
            private val surfaceDragDeadzonePx = thresholdPx * 0.18f

            private fun isAtTop(): Boolean {
                refs.scrollAtTopOverride?.let { return it() }
                refs.holder?.let { return it.get() }
                return refs.localScrollAtTop()
            }

            private fun setPull(offset: Float) {
                dragOffsetPx = offset
                val visual = if (offset <= surfaceDragDeadzonePx) {
                    0f
                } else {
                    offset - surfaceDragDeadzonePx
                }
                refs.applyPullVisual(visual)
            }

            private fun clearDragIfNeeded() {
                if (dragOffsetPx > 0f) setPull(0f)
            }

            private fun resetGesture() {
                contentScrolledThisGesture = false
                clearDragIfNeeded()
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                if (consumed.y != 0f && dragOffsetPx <= 0f) {
                    contentScrolledThisGesture = true
                }

                if (!refs.interactiveDismissActive()) {
                    if (contentScrolledThisGesture) {
                        return available
                    }
                    return Offset.Zero
                }
                if (refs.blockSurfaceDrag) {
                    clearDragIfNeeded()
                    return Offset.Zero
                }
                if (!shouldAllowSheetSurfaceDismiss(
                        atTop = isAtTop(),
                        contentScrolledThisGesture = contentScrolledThisGesture,
                        surfaceDragActive = true,
                        blockSurfaceDrag = false,
                    )
                ) {
                    clearDragIfNeeded()
                    return Offset.Zero
                }

                if (available.y > 0f || dragOffsetPx > 0f) {
                    val next = (dragOffsetPx + available.y).coerceAtLeast(0f)
                    val delta = next - dragOffsetPx
                    if (delta == 0f && available.y == 0f) return Offset.Zero
                    setPull(next)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                try {
                    if (!refs.interactiveDismissActive() || refs.blockSurfaceDrag || contentScrolledThisGesture) {
                        clearDragIfNeeded()
                        return Velocity.Zero
                    }
                    val offset = dragOffsetPx
                    if (offset <= 0f) return Velocity.Zero
                    val commit = offset >= thresholdPx ||
                        available.y > GlassGestureFlickVelocityPxPerSec
                    setPull(0f)
                    if (commit) refs.onDismiss()
                    return if (commit) available else Velocity.Zero
                } finally {
                    resetGesture()
                }
            }
        }
    }

    return this.nestedScroll(connection)
}

@Composable
fun SheetFingerDismissHost(
    onDismissRequest: () -> Unit,
    scrollAtTop: (() -> Boolean)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (LocalSheetFingerDismissInstalled.current) {
        content()
        return
    }
    val parentHolder = LocalSheetScrollAtTopHolder.current
    val ownedHolder = remember { SheetScrollAtTopHolder() }
    val holder = parentHolder ?: ownedHolder
    val resolvedScrollAtTop: () -> Boolean = scrollAtTop ?: { holder.get() }
    CompositionLocalProvider(
        LocalSheetScrollAtTopHolder provides holder,
        LocalSheetOnDismissRequest provides onDismissRequest,
        LocalSheetScrollAtTop provides resolvedScrollAtTop,
    ) {
        Box(
            modifier = modifier.sheetSwipeDismissWhenAtTop(
                onDismissRequest = onDismissRequest,
                scrollAtTop = resolvedScrollAtTop,
            ),
        ) {
            CompositionLocalProvider(LocalSheetFingerDismissInstalled provides true) {
                content()
            }
        }
    }
}

@Composable
fun rememberSheetScrollAtTop(scrollState: ScrollState): () -> Boolean =
    remember(scrollState) { { scrollState.isSheetScrollAtTop() } }

@Composable
fun rememberSheetScrollAtTop(listState: LazyListState): () -> Boolean =
    remember(listState) { { listState.isSheetScrollAtTop() } }
