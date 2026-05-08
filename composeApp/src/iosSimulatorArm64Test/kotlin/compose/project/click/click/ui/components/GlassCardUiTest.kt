package compose.project.click.click.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Compose Multiplatform UI tests ([runComposeUiTest]) for the iOS Simulator target.
 * Android JVM unit tests use [GlassCardTest] in androidUnitTest (design token); full UI
 * harness is not supported on the Android unit-test JVM.
 */
@OptIn(ExperimentalTestApi::class)
class GlassCardUiTest {

    @Test
    fun glassCard_rendersContentSlot() = runComposeUiTest {
        setContent {
            GlassCard {
                Text("slot content")
            }
        }
        onNodeWithText("slot content").assertExists()
    }

    @Test
    fun glassCard_onClick_invokesCallback() = runComposeUiTest {
        var clicks = 0
        setContent {
            GlassCard(onClick = { clicks++ }) {
                Text("inside card")
            }
        }
        onNodeWithText("inside card").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun glassAlertDialog_rendersTitleAndConfirm() = runComposeUiTest {
        setContent {
            GlassAlertDialog(
                onDismissRequest = {},
                title = { Text("Glass title") },
                text = { Text("Glass body") },
                confirmButton = {
                    TextButton(onClick = {}) { Text("OK") }
                },
            )
        }
        onNodeWithText("Glass title").assertExists()
        onNodeWithText("Glass body").assertExists()
        onNodeWithText("OK").assertExists()
    }
}
