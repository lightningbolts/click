@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.sheet // pragma: allowlist secret

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Shared bottom sheet shell for Click action, form, profile, and map beacon flows.
 *
 * Android: Calf [AdaptiveBottomSheet]; [expandable] maps to `skipPartiallyExpanded`.
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
    /** When true, sheet can expand to full height (Android partial allowed). */
    expandable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
)
