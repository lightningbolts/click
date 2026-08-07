package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Reports whether the active sheet body scroll is at the top.
 * Default true so non-scrollable sheets can dismiss from a downward pull.
 */
val LocalSheetScrollAtTop = compositionLocalOf { { true } }

/** Dismiss callback for the hosting bottom sheet. */
val LocalSheetOnDismissRequest = compositionLocalOf { {} }

/**
 * When true, [ClickSheetDialogChrome] hides its Compose grabber — the platform sheet already
 * draws one (UIKit page-sheet grabber or Material/Calf dragHandle).
 */
val LocalSheetUsesPlatformGrabber = compositionLocalOf { false }

/**
 * Drives the **entire** platform sheet surface (iOS page-sheet UIView). Never used to translate
 * Compose content inside fixed chrome.
 */
val LocalSheetSurfaceDragOffsetPx = compositionLocalOf<(Float) -> Unit> { {} }

val LocalSheetSurfaceDragActive = compositionLocalOf { false }

val LocalSheetFingerDismissInstalled = compositionLocalOf { false }

@Composable
fun ProvideSheetSwipeDismiss(
    onDismissRequest: () -> Unit,
    scrollAtTop: () -> Boolean = { true },
    content: @Composable () -> Unit,
) {
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

private val SheetFingerDismissThresholdDp = 88.dp

/**
 * Body swipe-to-dismiss coordination.
 *
 * - **Android (surface drag inactive):** pass-through so Material/Calf moves the real sheet.
 *   If this gesture already scrolled content, eat downward overscroll so dismiss waits for a
 *   new gesture.
 * - **iOS (surface drag active):** when already at top for this gesture, drag the page-sheet
 *   UIView; never translate Compose content (grabber stays on the curved top).
 */
@Composable
fun Modifier.sheetSwipeDismissWhenAtTop(
    onDismissRequest: () -> Unit = LocalSheetOnDismissRequest.current,
    scrollAtTop: () -> Boolean = LocalSheetScrollAtTop.current,
): Modifier {
    if (LocalSheetFingerDismissInstalled.current) return this

    val density = LocalDensity.current
    val thresholdPx = with(density) { SheetFingerDismissThresholdDp.toPx() }
    val dragOffsetPx = remember { mutableFloatStateOf(0f) }

    val onDismissUpdated by rememberUpdatedState(onDismissRequest)
    val scrollAtTopUpdated by rememberUpdatedState(scrollAtTop)
    val surfaceDragUpdated by rememberUpdatedState(LocalSheetSurfaceDragOffsetPx.current)
    val surfaceDragActive = LocalSheetSurfaceDragActive.current

    val connection = remember(
        thresholdPx,
        surfaceDragActive,
        onDismissUpdated,
        scrollAtTopUpdated,
        surfaceDragUpdated,
    ) {
        object : NestedScrollConnection {
            private var gestureActive = false
            private var contentScrolledThisGesture = false

            private fun noteUserInput() {
                if (gestureActive) return
                gestureActive = true
                contentScrolledThisGesture = false
            }

            private fun endGestureTracking() {
                gestureActive = false
                contentScrolledThisGesture = false
            }

            private fun setSurfaceDrag(offset: Float) {
                dragOffsetPx.floatValue = offset
                surfaceDragUpdated(offset)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                noteUserInput()

                // Any content scroll before a dismiss-drag means this gesture is a list scroll.
                if (dragOffsetPx.floatValue <= 0f && consumed.y != 0f) {
                    contentScrolledThisGesture = true
                }

                if (contentScrolledThisGesture) {
                    // Mid-list / same-gesture scroll-to-top: never start dismiss, never move surface.
                    if (dragOffsetPx.floatValue > 0f) setSurfaceDrag(0f)
                    if (available.y > 0f) return Offset(0f, available.y)
                    return Offset.Zero
                }

                if (!scrollAtTopUpdated()) return Offset.Zero

                if (!surfaceDragActive) {
                    // Android: let Material/Calf consume overscroll on the real sheet chrome.
                    return Offset.Zero
                }

                // iOS: own overscroll and move the page-sheet UIView as a whole.
                if (available.y > 0f || dragOffsetPx.floatValue > 0f) {
                    val next = (dragOffsetPx.floatValue + available.y).coerceAtLeast(0f)
                    val delta = next - dragOffsetPx.floatValue
                    if (delta == 0f && available.y == 0f) return Offset.Zero
                    setSurfaceDrag(next)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                try {
                    if (contentScrolledThisGesture) {
                        if (dragOffsetPx.floatValue > 0f) setSurfaceDrag(0f)
                        return if (available.y > 0f) available else Velocity.Zero
                    }
                    if (!surfaceDragActive) {
                        dragOffsetPx.floatValue = 0f
                        return Velocity.Zero
                    }
                    val offset = dragOffsetPx.floatValue
                    if (offset <= 0f) return Velocity.Zero
                    val commit = offset >= thresholdPx ||
                        available.y > GlassGestureFlickVelocityPxPerSec
                    setSurfaceDrag(0f)
                    if (commit) onDismissUpdated()
                    return if (commit) available else Velocity.Zero
                } finally {
                    endGestureTracking()
                }
            }
        }
    }

    return this.nestedScroll(connection)
}

@Composable
fun SheetFingerDismissHost(
    onDismissRequest: () -> Unit,
    scrollAtTop: () -> Boolean = { true },
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (LocalSheetFingerDismissInstalled.current) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalSheetOnDismissRequest provides onDismissRequest,
        LocalSheetScrollAtTop provides scrollAtTop,
    ) {
        Box(
            modifier = modifier.sheetSwipeDismissWhenAtTop(
                onDismissRequest = onDismissRequest,
                scrollAtTop = scrollAtTop,
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
