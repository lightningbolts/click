package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/**
 * Material [ModalBottomSheet] configured for the Click floating-glass / OLED sheet aesthetic.
 * Scrim opacity tracks sheet offset when [SheetState.requireOffset] is available (Z-depth feel).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    contentWindowInsets: androidx.compose.foundation.layout.WindowInsets = BottomSheetDefaults.windowInsets,
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    var offsetPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(sheetState) {
        snapshotFlow {
            runCatching { sheetState.requireOffset() }.getOrDefault(0f)
        }.collect { offsetPx = it }
    }
    val travelPx = with(density) { 420.dp.toPx() }.coerceAtLeast(1f)
    val expandAmount = (1f - (offsetPx.absoluteValue / travelPx).coerceIn(0f, 1f))
    val scrimAlpha =
        (0.42f + (GlassSheetTokens.ScrimBaseAlpha - 0.42f) * expandAmount)
            .coerceIn(0.38f, GlassSheetTokens.ScrimBaseAlpha)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        sheetMaxWidth = sheetMaxWidth,
        containerColor = GlassSheetTokens.OledBlack,
        contentColor = GlassSheetTokens.OnOled,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = scrimAlpha),
        dragHandle = { GlassSheetGrabber() },
        shape = RoundedCornerShape(
            topStart = GlassSheetTokens.SheetTopCorner,
            topEnd = GlassSheetTokens.SheetTopCorner,
        ),
        properties = properties,
        contentWindowInsets = { contentWindowInsets },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassSheetTokens.OledBlack),
            content = content,
        )
    }
}
