package compose.project.click.click.ui.sheet // pragma: allowlist secret

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import compose.project.click.click.ui.components.GlassAdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState

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

    val sheetState = rememberAdaptiveSheetState(
        skipPartiallyExpanded = !expandable,
        confirmValueChange = { true },
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
            sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
            scrimColor = scrimColor,
            contentWindowInsets = contentWindowInsets,
            content = content,
        )
    }
}
