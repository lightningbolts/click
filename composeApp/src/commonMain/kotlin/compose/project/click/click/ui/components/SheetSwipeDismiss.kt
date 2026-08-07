package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
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

val LocalSheetScrollAtTop = compositionLocalOf { { true } }

val LocalSheetOnDismissRequest = compositionLocalOf { {} }

/**
 * When true, [ClickSheetDialogChrome] hides its Compose grabber because the host draws one
 * (UIKit system grabber / Material dragHandle).
 */
val LocalSheetUsesPlatformGrabber = compositionLocalOf { false }

/**
 * When true (iOS UIKit [UIScrollView] host), Compose must not use [verticalScroll] —
 * the host owns scroll-edge expand and swipe-to-dismiss. Use [Modifier.sheetBodyScroll].
 */
val LocalSheetScrollOwnedByHost = compositionLocalOf { false }

/** Translates the entire page-sheet UIView (iOS). Never translate Compose content alone. */
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

private val SheetFingerDismissThresholdDp = 88.dp

/**
 * - Android / Material: same-gesture gate only (leave leftovers for sheet).
 * - iOS fill sheets: pull-down at scroll top translates the whole page-sheet UIView.
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

            private fun noteUserInput() {
                if (gestureActive) return
                gestureActive = true
            }

            private fun endGestureTracking() {
                gestureActive = false
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

                if (!scrollAtTopUpdated()) return Offset.Zero

                if (!surfaceDragActive) {
                    // Material/Calf / UIScrollView host — leave leftovers to the sheet.
                    return Offset.Zero
                }

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
