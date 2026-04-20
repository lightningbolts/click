package compose.project.click.click.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ChatAmbientLayoutUiTest {

    @Test
    fun ambientMesh_andGlassPlates_registerInLayoutTree() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ChatAmbientMeshBackground(
                            connection = null,
                            isHubNeutral = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                        ChatLiquidGlassPlate(
                            modifier = Modifier.fillMaxSize(),
                            testTag = ChatGlassHeaderPlateTestTag,
                        )
                    }
                }
            }
        }
        onNodeWithTag(ChatAmbientMeshTestTag).assertIsDisplayed()
        onNodeWithTag(ChatGlassHeaderPlateTestTag).assertIsDisplayed()
    }
}
