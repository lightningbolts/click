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
 * Trailing iOS navigation-bar button described with an SF Symbol. Android ignores this and uses
 * the Compose [NativeCollapsingScaffold] `actions` slot instead.
 */
data class NativeChromeAction(
    val sfSymbol: String,
    val contentDescription: String,
    val onClick: () -> Unit,
)

/**
 * Header chrome is platform-native on purpose; folding this back into a shared
 * [CollapsibleGlassTopBar] reintroduces hide-instead-of-collapse and fake glass.
 *
 * Android uses Material 3 [androidx.compose.material3.LargeTopAppBar] with
 * `exitUntilCollapsedScrollBehavior`. iOS attaches a real `UINavigationBar` to the Compose
 * host view (same mounting as the liquid-glass `UITabBar`) — never a full-screen
 * `UIKitViewController` overlay. Title and bar buttons share one compact row (WhatsApp-style);
 * subtitle tucks away smoothly with nested scroll. iOS 26 leaves system Liquid Glass alone
 * (no custom `UINavigationBarAppearance`).
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
    onNavigateBack: (() -> Unit)? = null,
    nativeTrailingActions: List<NativeChromeAction> = emptyList(),
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
    onOpenSearch: (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
    nativeTrailingActions: List<NativeChromeAction> = emptyList(),
    horizontalPadding: Dp = AppScreenDefaults.HorizontalPadding,
    content: @Composable (Modifier) -> Unit,
)

/**
 * Hides the iOS host-view `UINavigationBar` while a screen draws its own header (chat, Tap to
 * Connect). No-op on Android.
 */
@Composable
expect fun HidePlatformNativeNavigationBar()

/**
 * Maps a lazy list's first-visible item into a monotonically increasing scroll offset so iOS
 * large-title chrome can switch `largeTitleDisplayMode` after the first row.
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
