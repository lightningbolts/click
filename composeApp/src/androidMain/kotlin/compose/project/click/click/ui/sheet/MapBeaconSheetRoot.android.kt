package compose.project.click.click.ui.sheet // pragma: allowlist secret

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import compose.project.click.click.ui.components.GlassAdaptiveBottomSheet
import compose.project.click.click.ui.components.GlassSheetGrabber
import compose.project.click.click.ui.components.LocalSheetOnDismissRequest
import compose.project.click.click.ui.components.LocalSheetUsesPlatformGrabber
import compose.project.click.click.ui.components.rememberGlassAdaptiveSheetState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun MapBeaconSheetRoot(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    scrimColor: Color,
    contentWindowInsets: @Composable () -> WindowInsets,
    appColorScheme: ColorScheme,
    appTypography: Typography,
    modifier: Modifier,
    expandable: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return

    // Single settled height (skip partial) — Partial↔Expanded with sheetMaxWidth caused a
    // sudden width expand/contract while dragging. Body dismiss still works via nested scroll.
    val sheetState = rememberGlassAdaptiveSheetState(
        skipPartiallyExpanded = true,
    )

    LaunchedEffect(Unit) {
        try {
            sheetState.show()
        } catch (_: Exception) {
        }
    }

    MaterialTheme(
        colorScheme = appColorScheme,
        typography = appTypography,
    ) {
        GlassAdaptiveBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier.fillMaxWidth(),
            adaptiveSheetState = sheetState,
            // Unspecified = always full window width (no max-width snap during drag).
            sheetMaxWidth = Dp.Unspecified,
            scrimColor = scrimColor,
            contentWindowInsets = contentWindowInsets,
            dragHandle = { GlassSheetGrabber() },
            content = {
                CompositionLocalProvider(
                    LocalSheetOnDismissRequest provides onDismissRequest,
                    LocalSheetUsesPlatformGrabber provides true,
                ) {
                    content()
                }
            },
        )
    }
}
