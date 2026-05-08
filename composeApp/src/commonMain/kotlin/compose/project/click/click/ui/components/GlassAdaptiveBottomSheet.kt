package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.AdaptiveSheetState

/**
 * Calf [AdaptiveBottomSheet] with OLED shell + [GlassSheetGrabber], matching Material
 * [GlassModalBottomSheet] aesthetics on iOS and Android.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassAdaptiveBottomSheet(
    onDismissRequest: () -> Unit,
    adaptiveSheetState: AdaptiveSheetState,
    modifier: Modifier = Modifier,
    sheetMaxWidth: androidx.compose.ui.unit.Dp = BottomSheetDefaults.SheetMaxWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    AdaptiveBottomSheet(
        onDismissRequest = onDismissRequest,
        adaptiveSheetState = adaptiveSheetState,
        modifier = modifier,
        sheetMaxWidth = sheetMaxWidth,
        containerColor = GlassSheetTokens.OledBlack,
        contentColor = GlassSheetTokens.OnOled,
        dragHandle = { GlassSheetGrabber() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(GlassSheetTokens.OledBlack),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                content = content,
            )
        }
    }
}
