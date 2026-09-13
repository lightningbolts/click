package compose.project.click.click.ui.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ClickLaunchPrimitivesTest {
    @Test
    fun screenSpacing_matchesNativeTitleGutterAndEightPtScale() {
        assertEquals(20.dp, ClickScreenSpacing.Horizontal)
        assertEquals(24.dp, ClickScreenSpacing.Section)
        assertEquals(8.dp, ClickScreenSpacing.RowGap)
        assertEquals(8.dp, ClickScreenSpacing.Compact)
    }

    @Test
    fun appScreenDefaults_delegateToClickScreenSpacing() {
        assertEquals(ClickScreenSpacing.Horizontal, AppScreenDefaults.HorizontalPadding)
        assertEquals(ClickScreenSpacing.Section, AppScreenDefaults.SectionSpacing)
    }

    @Test
    fun listRow_staysWithinSocialInboxDensity() {
        assertEquals(64.dp, ClickListRowHeight)
        assertEquals(72.dp, ClickPlatformListRowHeight)
    }

    @Test
    fun sheetHorizontalPadding_matchesRootGutter() {
        assertEquals(ClickScreenSpacing.Horizontal, ClickSheetDefaults.ContentHorizontalPadding)
        assertEquals(ClickScreenSpacing.Section, ClickSheetDefaults.ContentBottomPadding)
    }
}
