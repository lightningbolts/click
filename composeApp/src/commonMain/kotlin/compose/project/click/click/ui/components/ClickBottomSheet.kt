package compose.project.click.click.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.sheet.AdaptiveSheetState
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
import compose.project.click.click.ui.sheet.MapBeaconSheetRoot

/** Shared padding and typography for every Click bottom sheet. */
object ClickSheetDefaults {
    val ContentHorizontalPadding = 20.dp
    val ContentBottomPadding = 24.dp
    val TitleBottomSpacing = 12.dp
    val ScrimAlpha = 0.55f
}

/**
 * Platform sheet shell matching map beacon dialogs:
 * iOS native medium detent page sheet; Android Calf adaptive sheet capped at half height.
 */
@Composable
fun ClickPlatformSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetColor = GlassSheetTokens.OledBlack
    val onSheet = GlassSheetTokens.OnOled
    MapBeaconSheetRoot(
        visible = true,
        onDismissRequest = onDismissRequest,
        containerColor = sheetColor,
        contentColor = onSheet,
        scrimColor = Color.Black.copy(alpha = ClickSheetDefaults.ScrimAlpha),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        appColorScheme = MaterialTheme.colorScheme,
        appTypography = MaterialTheme.typography,
        modifier = modifier,
        expandable = true,
    ) {
        ClickSheetDialogChrome(
            sheetColor = sheetColor,
            onSurface = onSheet,
            alignSemanticColorsToSheet = true,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
        }
    }
}

/**
 * OLED sheet body wrapper — use inside [ClickPlatformSheet].
 */
@Composable
fun ClickSheetChrome(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(GlassSheetTokens.OledBlack)
            .padding(bottom = ClickSheetDefaults.ContentBottomPadding),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = GlassSheetTokens.OnOled,
            )
            Spacer(Modifier.height(ClickSheetDefaults.TitleBottomSpacing))
        }
        content()
    }
}

/** Short action menus (message options, connection options, hub actions). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClickActionBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") sheetState: SheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = false),
    @Suppress("UNUSED_PARAMETER") sheetMaxWidth: Dp = Dp.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    ClickPlatformSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        content = content,
    )
}

/** Forms and tall content (profile, availability, connection context, verified click). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClickFormBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") adaptiveSheetState: AdaptiveSheetState =
        rememberAdaptiveSheetState(skipPartiallyExpanded = true),
    @Suppress("UNUSED_PARAMETER") sheetMaxWidth: Dp = Dp.Unspecified,
    @Suppress("UNUSED_PARAMETER") scrimColor: Color =
        Color.Black.copy(alpha = ClickSheetDefaults.ScrimAlpha),
    @Suppress("UNUSED_PARAMETER") contentWindowInsets: @Composable () -> WindowInsets =
        { WindowInsets(0, 0, 0, 0) },
    @Suppress("UNUSED_PARAMETER") dragHandle: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    ClickPlatformSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        content = content,
    )
}
