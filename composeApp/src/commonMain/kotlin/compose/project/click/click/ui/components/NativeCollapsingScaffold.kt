@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

data class NativeChromeMenuItem(
    val title: String,
    val sfSymbol: String? = null,
    val onClick: () -> Unit,
)

/**
 * Trailing iOS navigation-bar button described with an SF Symbol. Android ignores this and uses
 * the Compose [NativeCollapsingScaffold] `actions` slot instead.
 *
 * When [menuItems] is non-empty, iOS shows a native `UIMenu` (no Compose popup).
 */
data class NativeChromeAction(
    val sfSymbol: String,
    val contentDescription: String,
    val onClick: () -> Unit,
    val menuItems: List<NativeChromeMenuItem> = emptyList(),
)

/**
 * Leading identity for pushed native headers (chat). Uses [avatarFaceSpec] — the same
 * initials, seed color, and photo URL as [ConnectionListUserAvatarFace]. Tapping opens
 * the existing profile sheet.
 */
data class NativeChromeIdentity(
    val displayName: String?,
    val email: String?,
    val avatarUrl: String?,
    val userId: String,
    val onClick: () -> Unit,
)

/**
 * Inactive [AnimatedContent] tab roots must not steal the tab-layer `UINavigationBar`.
 * Covering routes (chat, settings subpages, Add Click QR/NFC/Tap, Nearby) use a second
 * overlay layer and clip the live destination chrome to the uncovered leading strip —
 * do not flip this local for those covers or the destination header remounts after
 * swipe-back. Swipe-back *underlays* (Home behind Map) must keep this false so a stale
 * Add Click title cannot paint over Map at gesture start.
 */
val LocalNativeChromeActive = staticCompositionLocalOf { true }

/**
 * Header chrome is platform-native on purpose; folding this back into a shared
 * [CollapsibleGlassTopBar] reintroduces hide-instead-of-collapse and fake glass.
 *
 * Android uses Material 3 [androidx.compose.material3.LargeTopAppBar] with
 * `exitUntilCollapsedScrollBehavior`. iOS attaches a real `UINavigationBar` to the Compose
 * host view (same mounting as the liquid-glass `UITabBar`) — never a full-screen
 *         `UIKitViewController` overlay. At rest the title is large-title size (34pt, wrapping
 * up to 2 lines) on the same row as glass bar buttons; the title column wraps before the
 * action cluster. Nested scroll collapses it into a compact centered title.
 * The collapsed bar stays translucent (WhatsApp-style). iOS 26 leaves system Liquid Glass
 * alone (no custom `UINavigationBarAppearance`).
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
 * Connect). Covers **tab + overlay**. Do not use for Click Drops — that must keep `UITabBar`
 * and replace overlay chrome via [CoverPlatformOverlayNavigationBar] + [BindPlatformNativeNavigationBar]
 * with `leadingClose`. No-op on Android.
 */
@Composable
expect fun HidePlatformNativeNavigationBar()

/**
 * Covers only the overlay `UINavigationBar` (chat header) so a camera / sheet can rebind it.
 * Does **not** hide the tab bar, unbind chat chrome, or flip `LocalNativeChromeActive`.
 * Dismiss must yield exclusive overlay ownership so the conversation header restores.
 * No-op on Android.
 */
@Composable
expect fun CoverPlatformOverlayNavigationBar()

/**
 * While a covering sub-screen is interactively sliding away, translate the native bar with it
 * so the previous header (Compose underlay) is revealed underneath — never painted on top.
 * No-op on Android.
 */
@Composable
expect fun PlatformNativeNavigationBarSwipeReveal(revealPx: MutableFloatState)

/**
 * Lets conversation / scanner overlays own the iOS host `UINavigationBar` (glass back + actions)
 * instead of hiding it and drawing Compose fakes. No-op on Android.
 */
@Composable
expect fun BindPlatformNativeNavigationBar(
    title: String,
    subtitle: String? = null,
    presenceOnline: Boolean? = null,
    identity: NativeChromeIdentity? = null,
    onNavigateBack: (() -> Unit)? = null,
    onOpenSearch: (() -> Unit)? = null,
    nativeTrailingActions: List<NativeChromeAction> = emptyList(),
    collapseFraction: Float = 1f,
    leadingClose: Boolean = false,
)

data class NativeMapLayerOption(
    val id: String,
    val label: String,
    val selected: Boolean,
)

/**
 * iOS host-sibling liquid-glass map controls (layer menu, drop, zoom). No-op on Android
 * where [compose.project.click.click.ui.screens] draws Compose glass pills.
 */
@Composable
expect fun PlatformNativeMapFloatingChrome(
    visible: Boolean,
    layerLabel: String,
    layerOptions: List<NativeMapLayerOption>,
    onToggleLayerId: (String) -> Unit,
    onDropBeacon: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    bottomPadding: Dp,
)

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
