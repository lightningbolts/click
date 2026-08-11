package compose.project.click.click.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    /** Clearance under the iOS system grabber / sheet handle before primary chrome. */
    val ContentTopPaddingUnderGrabber = 20.dp
    val TitleBottomSpacing = 12.dp
    val ScrimAlpha = 0.55f
}

/**
 * Platform sheet shell matching map beacon dialogs:
 * iOS UIKit page sheet (Liquid Glass + system grabber); Android Calf adaptive sheet.
 */
@Composable
fun ClickPlatformSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    /** When true, sheet can expand to full height (medium+large / Android partial). */
    expandable: Boolean = true,
    /** iOS: host in UIScrollView for dismiss-at-top. Prefer false for sticky-IME short forms. */
    useUiKitScrollHost: Boolean = true,
    /** iOS: fill sheet viewport (LazyColumn / pager). Requires [useUiKitScrollHost]. */
    uiKitFillViewport: Boolean = false,
    contentWindowInsets: @Composable () -> WindowInsets = { WindowInsets(0, 0, 0, 0) },
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetColor = MaterialTheme.colorScheme.surface
    val onSheet = MaterialTheme.colorScheme.onSurface
    val fillBody = !useUiKitScrollHost || uiKitFillViewport
    MapBeaconSheetRoot(
        visible = true,
        onDismissRequest = onDismissRequest,
        containerColor = sheetColor,
        contentColor = onSheet,
        scrimColor = Color.Black.copy(alpha = ClickSheetDefaults.ScrimAlpha),
        contentWindowInsets = contentWindowInsets,
        appColorScheme = MaterialTheme.colorScheme,
        appTypography = MaterialTheme.typography,
        modifier = modifier,
        expandable = expandable,
        useUiKitScrollHost = useUiKitScrollHost,
        uiKitFillViewport = uiKitFillViewport,
    ) {
        // Nested ProvideSheetSwipeDismiss reports scroll-at-top into SheetFingerDismissHost's
        // holder (iOS fill sheets / Android adaptive). Do not nest a second holder here.
        CompositionLocalProvider(
            LocalSheetOnDismissRequest provides onDismissRequest,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fillBody) Modifier.fillMaxSize() else Modifier),
            ) {
                ClickSheetDialogChrome(
                    sheetColor = sheetColor,
                    onSurface = onSheet,
                    alignSemanticColorsToSheet = true,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (fillBody) Modifier.fillMaxHeight() else Modifier),
                        content = content,
                    )
                }
            }
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
            .background(sheetPageBackground())
            .padding(bottom = ClickSheetDefaults.ContentBottomPadding),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = GlassSheetTokens.OnOled(),
            )
            Spacer(modifier.height(ClickSheetDefaults.TitleBottomSpacing))
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

/**
 * Forms and tall text-entry content.
 *
 * Defaults to UIKit scroll-host for Column wrap-content (which-pin / view-event).
 * Pass [useUiKitScrollHost]=false for LazyColumn / pager / sticky-IME sheets.
 * Text fields use [sheetImePadding] — Compose `imePadding()` is unreliable in UIKit sheets.
 */
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
    contentWindowInsets: @Composable () -> WindowInsets = { WindowInsets.ime },
    @Suppress("UNUSED_PARAMETER") dragHandle: @Composable () -> Unit = {},
    /**
     * iOS: true (default) for Column/`sheetBodyScroll` wrap-content sheets (which-pin /
     * view-event). Set **false** for sticky-IME short forms (availability / drop).
     * Pair with [uiKitFillViewport] for LazyColumn / HorizontalPager sheets.
     */
    useUiKitScrollHost: Boolean = true,
    /** iOS: fill sheet viewport for lists/pagers. Requires [useUiKitScrollHost]=true. */
    uiKitFillViewport: Boolean = false,
    expandable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ClickPlatformSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        expandable = expandable,
        useUiKitScrollHost = useUiKitScrollHost,
        uiKitFillViewport = uiKitFillViewport,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}
