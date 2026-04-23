package compose.project.click.click.ui.sheet // pragma: allowlist secret

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Bottom sheet shell for **map beacon flows only** (drop pin + beacon detail).
 * Connection/profile sheet on the map stays on [com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet] unchanged.
 *
 * Android: Calf [AdaptiveBottomSheet] with a hard half-height cap on the sheet.
 * iOS: native `UISheet` with **medium detent only** (Calf’s iOS path always uses `.largeDetent()` when
 * `skipPartiallyExpanded = true`, which forces full-screen and causes gray/white bands above short content).
 */
@Composable
expect fun MapBeaconSheetRoot(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    scrimColor: Color,
    contentWindowInsets: @Composable () -> WindowInsets,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
)
