package compose.project.click.click.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * Shared OLED sheet chrome (grabber + themed body) used by map beacon sheets and all
 * [ClickPlatformSheet] dialogs.
 */
@Composable
fun ClickSheetDialogChrome(
    modifier: Modifier = Modifier,
    sheetColor: Color = GlassSheetTokens.OledBlack,
    onSurface: Color = GlassSheetTokens.OnOled,
    useGrabber: Boolean = true,
    alignSemanticColorsToSheet: Boolean = true,
    content: @Composable () -> Unit,
) {
    val grabberTint = if (alignSemanticColorsToSheet) {
        val darkSheet = sheetColor.luminance() < 0.05f
        if (darkSheet) GlassSheetTokens.OnOledMuted.copy(alpha = 0.42f)
        else contentColorFor(sheetColor).copy(alpha = 0.38f)
    } else {
        onSurface.copy(alpha = 0.3f)
    }

    @Composable
    fun themedContent() {
        if (alignSemanticColorsToSheet) {
            val darkSheet = sheetColor.luminance() < 0.05f
            val primaryOn =
                if (darkSheet) GlassSheetTokens.OnOled else contentColorFor(sheetColor)
            val mutedOn =
                if (darkSheet) GlassSheetTokens.OnOledMuted
                else lerp(sheetColor, primaryOn, 0.88f)
            val elevatedSurface =
                if (darkSheet) GlassSheetTokens.GlassSurface
                else lerp(sheetColor, primaryOn, 0.12f)
            val scheme = MaterialTheme.colorScheme
            MaterialTheme(
                colorScheme = scheme.copy(
                    surface = sheetColor,
                    surfaceContainerLow = sheetColor,
                    surfaceContainer = sheetColor,
                    surfaceContainerHigh = sheetColor,
                    surfaceContainerHighest = elevatedSurface,
                    onSurface = primaryOn,
                    onSurfaceVariant = mutedOn,
                    outline = primaryOn.copy(alpha = 0.34f),
                    outlineVariant = primaryOn.copy(alpha = 0.26f),
                    primary = scheme.primary,
                    onPrimary = scheme.onPrimary,
                    error = scheme.error,
                    onError = scheme.onError,
                ),
            ) {
                content()
            }
        } else {
            content()
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(sheetColor),
    ) {
        if (useGrabber) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(grabberTint),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
        ) {
            themedContent()
        }
    }
}
