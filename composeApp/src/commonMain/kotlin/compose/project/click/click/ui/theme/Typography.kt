package compose.project.click.click.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import click.composeapp.generated.resources.Manrope_ExtraBold
import click.composeapp.generated.resources.Res
import click.composeapp.generated.resources.manrope_bold
import click.composeapp.generated.resources.manrope_medium
import click.composeapp.generated.resources.manrope_regular
import click.composeapp.generated.resources.manrope_semibold
import org.jetbrains.compose.resources.Font

val ManropeFontFamily: FontFamily
    @Composable
    get() =
        FontFamily(
            Font(Res.font.manrope_regular, FontWeight.Normal),
            Font(Res.font.manrope_medium, FontWeight.Medium),
            Font(Res.font.manrope_semibold, FontWeight.SemiBold),
            Font(Res.font.manrope_bold, FontWeight.Bold),
            Font(Res.font.Manrope_ExtraBold, FontWeight.ExtraBold),
        )

/**
 * "Quiet Presence" type (click-web DESIGN.md, iOS DesignSystem/Typography.swift): Manrope ExtraBold
 * for display and section titles only; every other role uses the platform face (Roboto) so body
 * text reads like the rest of Android. Nothing is smaller than 12sp.
 *
 * | Role | Size/line | Face |
 * |---|---|---|
 * | display* | 44/48 · 40/44 · 34/40 | Manrope 800 |
 * | headlineLarge / Medium / Small | 28/34 · 24/30 · 22/28 | Manrope 800 |
 * | titleLarge | 22/28 | Manrope 800 |
 * | titleMedium / Small | 17/24 · 15/20 | system 600 |
 * | bodyLarge / Medium / Small | 17/24 · 15/22 · 13/18 | system 400 |
 * | labelLarge / Medium / Small | 15/20 · 13/18 · 12/16 | system 600 |
 */
@Composable
fun clickTypography(): Typography {
    val display = ManropeFontFamily
    val text = FontFamily.Default

    fun heading(
        size: Int,
        line: Int,
        tracking: Double = 0.0,
    ) = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )

    fun body(
        size: Int,
        line: Int,
        weight: FontWeight,
    ) = TextStyle(fontFamily = text, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)
    return Typography(
        displayLarge = heading(44, 48, -0.04),
        displayMedium = heading(40, 44, -0.03),
        displaySmall = heading(34, 40, -0.02),
        headlineLarge = heading(28, 34, -0.02),
        headlineMedium = heading(24, 30),
        headlineSmall = heading(22, 28),
        titleLarge = heading(22, 28),
        titleMedium = body(17, 24, FontWeight.SemiBold),
        titleSmall = body(15, 20, FontWeight.SemiBold),
        bodyLarge = body(17, 24, FontWeight.Normal),
        bodyMedium = body(15, 22, FontWeight.Normal),
        bodySmall = body(13, 18, FontWeight.Normal),
        labelLarge = body(15, 20, FontWeight.SemiBold),
        labelMedium = body(13, 18, FontWeight.SemiBold),
        labelSmall = body(12, 16, FontWeight.SemiBold),
    )
}

/**
 * Input text style for Material text fields.
 *
 * Fields use a centered 24sp line box with no trim, so descenders (g/y/p) are never clipped.
 */
@Composable
fun clickTextFieldTextStyle(): TextStyle =
    MaterialTheme.typography.bodyLarge.copy(
        lineHeight = 24.sp,
        lineHeightStyle =
            LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
    )
