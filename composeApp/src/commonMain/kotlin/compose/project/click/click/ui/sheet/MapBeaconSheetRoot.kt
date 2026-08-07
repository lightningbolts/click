package compose.project.click.click.ui.sheet // pragma: allowlist secret

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Shared bottom sheet shell for Click action, form, profile, and map beacon flows.
 *
 * Android: Calf [AdaptiveBottomSheet]; [expandable] maps to `skipPartiallyExpanded`.
 * iOS: native [UISheetPresentationController] with the app-themed page surface under the
 * system presentation material.
 * Compose fills the detent (no UIScrollView content gap / white strips). iOS scroll-hosted
 * bodies use [sheetBodyScroll] so UIKit owns the native system grabber, expand, and dismiss
 * interaction; nested Compose lists use the platform surface drag host instead.
 *
 * [appColorScheme] / [appTypography] are re-applied so sheet chrome matches the app theme.
 */
@Composable
expect fun MapBeaconSheetRoot(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    scrimColor: Color,
    contentWindowInsets: @Composable () -> WindowInsets,
    appColorScheme: ColorScheme,
    appTypography: Typography,
    modifier: Modifier = Modifier,
    /** When true, sheet can expand to full height (iOS medium+large / Android partial allowed). */
    expandable: Boolean = true,
    /**
     * iOS only: host body in UIScrollView for system dismiss-at-top. Disable for LazyColumn sheets.
     * Ignored on Android.
     */
    useUiKitScrollHost: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
)
