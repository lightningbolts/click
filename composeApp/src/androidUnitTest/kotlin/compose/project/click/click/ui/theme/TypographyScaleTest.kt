package compose.project.click.click.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalTestApi::class)
class TypographyScaleTest {
    private fun capture(): Typography {
        var typography: Typography? = null
        runComposeUiTest { setContent { typography = clickTypography() } }
        return typography!!
    }

    @Test
    fun nothingIsSmallerThanTwelveSp() {
        val t = capture()
        listOf(
            t.displayLarge,
            t.displayMedium,
            t.displaySmall,
            t.headlineLarge,
            t.headlineMedium,
            t.headlineSmall,
            t.titleLarge,
            t.titleMedium,
            t.titleSmall,
            t.bodyLarge,
            t.bodyMedium,
            t.bodySmall,
            t.labelLarge,
            t.labelMedium,
            t.labelSmall,
        ).forEach { assertTrue(it.fontSize.value >= 12f, "${it.fontSize}") }
    }

    @Test
    fun manropeForHeadingsOnlySystemFaceForText() {
        val t = capture()
        assertEquals(FontFamily.Default, t.bodyLarge.fontFamily)
        assertEquals(FontFamily.Default, t.labelMedium.fontFamily)
        assertEquals(FontFamily.Default, t.titleMedium.fontFamily)
        assertNotEquals(FontFamily.Default, t.headlineSmall.fontFamily)
        assertEquals(FontWeight.ExtraBold, t.titleLarge.fontWeight)
        assertEquals(FontWeight.Normal, t.bodyMedium.fontWeight)
        assertEquals(17f, t.bodyLarge.fontSize.value)
    }
}
