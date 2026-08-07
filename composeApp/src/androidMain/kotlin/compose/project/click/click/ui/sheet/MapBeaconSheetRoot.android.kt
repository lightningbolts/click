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
    useUiKitScrollHost: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return

    // Android ignores useUiKitScrollHost — Material/Calf owns nested scroll.
    @Suppress("UNUSED_VARIABLE")
    val ignoredScrollHost = useUiKitScrollHost

    val sheetState = rememberGlassAdaptiveSheetState(
        skipPartiallyExpanded = !expandable,
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
            // Full window width — avoids max-width snap during drag without blocking expand.
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
