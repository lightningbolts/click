package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ConnectionContextSheetUiTest {

    @Test
    fun connectionContextSheet_sparkNew_showsConnectCopy() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Surface {
                    ConnectionContextSheet(
                        connectedUsers = emptyList(),
                        locationName = null,
                        initialNoiseOptIn = true,
                        noisePermissionGranted = true,
                        onDismiss = {},
                        onConfirm = { _, _ -> },
                        intentHint = ConnectionIntentHint.SparkNew("Alex"),
                    )
                }
            }
        }
        onNodeWithText("Sparking a new connection...", substring = true).assertExists()
        onNodeWithText("Connect", substring = true).assertExists()
    }

    @Test
    fun connectionContextSheet_existing_showsSaveEncounterCopy() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Surface {
                    ConnectionContextSheet(
                        connectedUsers = emptyList(),
                        locationName = null,
                        initialNoiseOptIn = true,
                        noisePermissionGranted = true,
                        onDismiss = {},
                        onConfirm = { _, _ -> },
                        intentHint = ConnectionIntentHint.LogExistingEncounter("Jordan"),
                        onSaveEncounterOnly = { _, _ -> },
                    )
                }
            }
        }
        onNodeWithText("Logging encounter with Jordan", substring = true).assertExists()
        onNodeWithText("Save Encounter", substring = true).assertExists()
    }
}
