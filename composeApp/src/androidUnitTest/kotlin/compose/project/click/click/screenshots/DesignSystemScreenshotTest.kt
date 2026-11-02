@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import compose.project.click.click.ui.components.AppEmptyState
import compose.project.click.click.ui.components.ClickButton
import compose.project.click.click.ui.components.ClickButtonVariant
import compose.project.click.click.ui.theme.PlatformThemeProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines for the design system (colors, type ramp, buttons, empty state) in light
 * and dark. Record with `./gradlew :composeApp:recordRoborazziDebug`; CI runs
 * `verifyRoborazziDebug` against the PNGs in `composeApp/src/androidUnitTest/screenshots/`.
 * A plain `testDebugUnitTest` renders these without comparing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1400dp-xxhdpi")
class DesignSystemScreenshotTest {
    @Test
    fun designSystemLight() = captureRoboImage("$SCREENSHOTS/designSystem_light.png") { Gallery(dark = false) }

    @Test
    fun designSystemDark() = captureRoboImage("$SCREENSHOTS/designSystem_dark.png") { Gallery(dark = true) }
}

/** Relative to the module (Roborazzi resolves explicit paths from the Gradle project dir). */
internal const val SCREENSHOTS = "src/androidUnitTest/screenshots"

@Composable
private fun Gallery(dark: Boolean) {
    PlatformThemeProvider(isDarkMode = dark) {
        val scheme = MaterialTheme.colorScheme
        Column(
            modifier =
                Modifier
                    .width(411.dp)
                    .fillMaxHeight()
                    .background(scheme.background)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Colors", style = MaterialTheme.typography.titleLarge, color = scheme.onBackground)
            listOf(
                "primary" to scheme.primary,
                "primaryContainer" to scheme.primaryContainer,
                "secondary" to scheme.secondary,
                "surface" to scheme.surface,
                "surfaceVariant" to scheme.surfaceVariant,
                "onSurface" to scheme.onSurface,
                "onSurfaceVariant" to scheme.onSurfaceVariant,
                "outline" to scheme.outline,
                "error" to scheme.error,
            ).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (name, color) -> Swatch(name, color) }
                }
            }
            Text("Type", style = MaterialTheme.typography.titleLarge, color = scheme.onBackground)
            val type = MaterialTheme.typography
            listOf(
                "displaySmall" to type.displaySmall,
                "headlineMedium" to type.headlineMedium,
                "titleLarge" to type.titleLarge,
                "titleMedium" to type.titleMedium,
                "bodyLarge" to type.bodyLarge,
                "bodyMedium" to type.bodyMedium,
                "bodySmall" to type.bodySmall,
                "labelMedium" to type.labelMedium,
            ).forEach { (name, style) -> TypeRow(name, style) }
            Text("Buttons", style = MaterialTheme.typography.titleLarge, color = scheme.onBackground)
            ClickButtonVariant.entries.forEach { variant ->
                ClickButton(onClick = {}, variant = variant, modifier = Modifier.fillMaxWidth()) {
                    Text(variant.name)
                }
            }
            AppEmptyState(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = "No Clicks yet",
                body = "Tap phones with someone to make your first Click.",
            )
        }
    }
}

@Composable
private fun Swatch(
    name: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 116.dp, height = 40.dp)
                .background(color, RoundedCornerShape(10.dp)),
        )
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun TypeRow(
    name: String,
    style: TextStyle,
) {
    Text("$name · Click", style = style, color = MaterialTheme.colorScheme.onBackground)
}
