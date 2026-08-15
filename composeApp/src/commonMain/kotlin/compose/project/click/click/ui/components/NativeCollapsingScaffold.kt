@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Header chrome is platform-native on purpose; folding this back into a shared
 * [CollapsibleGlassTopBar] reintroduces hide-instead-of-collapse and fake glass.
 *
 * Android uses Material 3 [androidx.compose.material3.LargeTopAppBar] with
 * `exitUntilCollapsedScrollBehavior`. iOS uses a real `UINavigationController` with
 * `prefersLargeTitles` plus a companion `UIScrollView` mirrored from [LazyListState] so the
 * system bar performs large→inline collapse (including iOS 26 Liquid Glass). A system
 * `UIVisualEffectView` covers the status-bar / Dynamic Island band.
 *
 * The collapsed state is always a compact app bar — never `if (hidden) return`.
 */
@Composable
expect fun NativeCollapsingScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    presenceOnline: Boolean? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    showHeader: Boolean = true,
    belowHeaderSpacing: Dp = AppScreenDefaults.SectionSpacing,
    horizontalPadding: Dp = AppScreenDefaults.HorizontalPadding,
    lazyListState: LazyListState,
    headerBelowContent: @Composable (() -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit,
)

/**
 * Same native collapsing chrome as [NativeCollapsingScaffold] for `verticalScroll` bodies
 * (Add Click, My QR).
 */
@Composable
expect fun NativeCollapsingScrollScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    presenceOnline: Boolean? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    horizontalPadding: Dp = AppScreenDefaults.HorizontalPadding,
    content: @Composable (Modifier) -> Unit,
)

/**
 * Maps a lazy list's first-visible item into a monotonically increasing scroll offset so a
 * companion UIKit `UIScrollView` can drive large-title collapse after the first row.
 */
fun nativeChromeScrollOffsetPx(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Int =
    if (firstVisibleItemIndex <= 0) {
        firstVisibleItemScrollOffset.coerceAtLeast(0)
    } else {
        10_000 + firstVisibleItemScrollOffset.coerceAtLeast(0)
    }
