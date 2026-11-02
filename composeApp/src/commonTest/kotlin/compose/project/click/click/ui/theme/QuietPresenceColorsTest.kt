package compose.project.click.click.ui.theme // pragma: allowlist secret

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Token values and contrast pairs from click-web DESIGN.md and iOS DesignSystem/Colors.swift. */
class QuietPresenceColorsTest {
    @Test
    fun actionAndBrandMatchIosAndWeb() {
        assertEquals(Color(0xFF7C3AED), PrimaryBlue, "action fill")
        assertEquals(Color(0xFF630ED4), BrandMarkPurple, "brand mark only")
        assertEquals(Color(0xFF5A00C6), DeepBlue, "accent text light")
        assertEquals(Color(0xFFC3A6FF), AccentTextDark, "accent text dark")
        assertEquals(Color(0xFFEEE5FF), SoftBlue, "selection light")
        assertEquals(Color(0xFF24133F), SelectionDark, "selection dark")
    }

    @Test
    fun surfacesAndTextMatchWeb() {
        assertEquals(Color(0xFFF6F6F8), BackgroundLight)
        assertEquals(Color(0xFF0B0B0D), BackgroundDark)
        assertEquals(Color(0xFF1C1C1E), SurfaceDark)
        assertEquals(Color(0xFF111113), OnSurfaceLight)
        assertEquals(Color(0xFFF5F5F7), OnSurfaceDark)
        assertEquals(Color(0xFF55555C), OnSurfaceVariant)
        assertEquals(Color(0xFFAEAEB2), OnSurfaceVariantDark)
        assertEquals(Color(0xFFD70015), DestructiveLight)
        assertEquals(Color(0xFFFF453A), DestructiveDark)
    }

    private fun assertReadable(
        fg: Color,
        bg: Color,
        label: String,
    ) {
        val ratio = contrastRatio(fg, bg)
        assertTrue(ratio >= WCAG_BODY_TEXT_MIN_RATIO, "$label: $ratio < $WCAG_BODY_TEXT_MIN_RATIO")
    }

    @Test
    fun textRolesMeetBodyContrast() {
        assertReadable(Color.White, PrimaryBlue, "label on action")
        assertReadable(OnSurfaceLight, BackgroundLight, "text light")
        assertReadable(OnSurfaceVariant, SurfaceLight, "secondary text light")
        assertReadable(OutlineMuted, SurfaceLight, "tertiary text light")
        assertReadable(DeepBlue, SurfaceLight, "accent text light")
        assertReadable(OnSurfaceDark, BackgroundDark, "text dark")
        assertReadable(OnSurfaceVariantDark, SurfaceDark, "secondary text dark")
        assertReadable(OutlineMutedDark, SurfaceDark, "tertiary text dark")
        assertReadable(AccentTextDark, SurfaceDark, "accent text dark")
        assertReadable(AccentTextDark, SelectionDark, "selected chip dark")
        assertReadable(DeepBlue, SoftBlue, "selected chip light")
        // Destructive buttons: white on the light red, black on the bright dark red.
        assertReadable(Color.White, DestructiveLight, "destructive light")
        assertReadable(Color.Black, DestructiveDark, "destructive dark")
    }
}
