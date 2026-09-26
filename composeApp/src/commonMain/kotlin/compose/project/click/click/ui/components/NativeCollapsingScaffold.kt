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
 * `exitUntilCollapsedScrollBehavior`. Nested scroll collapses it into a compact title.
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
    onOpenSearch: (() -> Unit)? = null,
    /**
     * When true, [onOpenSearch] is omitted from the bar at rest and appears as a trailing glass
     * search button only after the header has collapsed (Home search pill → header button).
     */
    collapseSearchIntoBar: Boolean = false,
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
 * (Add Click, My QR). [scrollEnabled] disables both body scrolling and header collapse.
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
    scrollEnabled: Boolean = true,
    content: @Composable (Modifier) -> Unit,
)
