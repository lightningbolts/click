package compose.project.click.click.ui.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.components.ClickFormBottomSheet
import compose.project.click.click.ui.components.ClickSheetChrome
import compose.project.click.click.ui.components.sheetBodyScroll
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

/**
 * Regression: a titled [ClickSheetChrome] whose modifier scrolls (the hangout planner, Send Later,
 * Log a hangout…) crashed with "Vertically scrollable component was measured with an infinity
 * maximum height" because the title spacer reused the caller's scrolling modifier.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalTestApi::class)
class PlanHangoutSheetUiTest {
    @Test
    fun titledScrollingSheetMeasures() =
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    ClickFormBottomSheet(onDismissRequest = {}, fillBody = true) {
                        ClickSheetChrome(
                            title = "Plan with Sam",
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .sheetBodyScroll()
                                    .padding(horizontal = 16.dp),
                        ) {
                            Text("What")
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Plan with Sam").assertExists()
            onNodeWithText("What").assertExists()
        }
}
