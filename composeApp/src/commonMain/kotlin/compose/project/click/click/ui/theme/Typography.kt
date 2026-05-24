package compose.project.click.click.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import click.composeapp.generated.resources.Res
import click.composeapp.generated.resources.manrope_bold
import click.composeapp.generated.resources.manrope_medium
import click.composeapp.generated.resources.manrope_regular
import click.composeapp.generated.resources.manrope_semibold
import org.jetbrains.compose.resources.Font

val ManropeFontFamily: FontFamily
    @Composable
    get() = FontFamily(
        Font(Res.font.manrope_regular, FontWeight.Normal),
        Font(Res.font.manrope_medium, FontWeight.Medium),
        Font(Res.font.manrope_semibold, FontWeight.SemiBold),
        Font(Res.font.manrope_bold, FontWeight.Bold),
    )

private fun TextStyle.withManrope(family: FontFamily): TextStyle = copy(fontFamily = family)

@Composable
fun clickTypography(): Typography {
    val family = ManropeFontFamily
    val baseline = Typography()
    return Typography(
        displayLarge = baseline.displayLarge.withManrope(family),
        displayMedium = baseline.displayMedium.withManrope(family),
        displaySmall = baseline.displaySmall.withManrope(family),
        headlineLarge = baseline.headlineLarge.withManrope(family),
        headlineMedium = baseline.headlineMedium.withManrope(family),
        headlineSmall = baseline.headlineSmall.withManrope(family),
        titleLarge = baseline.titleLarge.withManrope(family),
        titleMedium = baseline.titleMedium.withManrope(family),
        titleSmall = baseline.titleSmall.withManrope(family),
        bodyLarge = baseline.bodyLarge.withManrope(family),
        bodyMedium = baseline.bodyMedium.withManrope(family),
        bodySmall = baseline.bodySmall.withManrope(family),
        labelLarge = baseline.labelLarge.withManrope(family),
        labelMedium = baseline.labelMedium.withManrope(family),
        labelSmall = baseline.labelSmall.withManrope(family),
    )
}
