package compose.project.click.click.ui.sheet // pragma: allowlist secret

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
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
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return

    val sheetState = rememberAdaptiveSheetState(
        skipPartiallyExpanded = true,
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
        AdaptiveBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f),
            adaptiveSheetState = sheetState,
            containerColor = containerColor,
            contentColor = contentColor,
            scrimColor = scrimColor,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            contentWindowInsets = contentWindowInsets,
            content = content,
        )
    }
}
