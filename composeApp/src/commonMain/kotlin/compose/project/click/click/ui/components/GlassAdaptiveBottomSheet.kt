package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.AdaptiveSheetState
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret

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
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    scrimColor: Color = Color.Black.copy(alpha = GlassSheetTokens.ScrimBaseAlpha),
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    dragHandle: @Composable () -> Unit = { GlassSheetGrabber() },
    content: @Composable ColumnScope.() -> Unit,
) {
    AdaptiveBottomSheet(
        onDismissRequest = onDismissRequest,
        adaptiveSheetState = adaptiveSheetState,
        modifier = modifier,
        sheetMaxWidth = sheetMaxWidth,
        containerColor = GlassSheetTokens.OledBlack,
        contentColor = GlassSheetTokens.OnOled,
        scrimColor = scrimColor,
        contentWindowInsets = contentWindowInsets,
        dragHandle = dragHandle,
    ) {
        val style = LocalPlatformStyle.current
        Box(modifier = Modifier.fillMaxWidth()) {
            if (style.isIOS) {
                PlatformGlassBackdrop(
                    modifier = Modifier.matchParentSize(),
                    cornerRadius = GlassSheetTokens.SheetTopCorner,
                    material = NativeLiquidGlassMaterial.DarkThin,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (style.isIOS) GlassSheetTokens.OledBlack.copy(alpha = 0.72f)
                        else GlassSheetTokens.OledBlack,
                    ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = content,
                )
            }
        }
    }
}
