package compose.project.click.click.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import compose.project.click.click.data.models.Message
import compose.project.click.click.ui.components.ClickChip
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalTestApi::class)
class StabilizationAccessibilityUiTest {
    @Test
    fun photoExposesOpenAndMessageActions() =
        runComposeUiTest {
            var opened = 0
            var actions = 0
            setContent {
                MaterialTheme {
                    ChatBubblePhotoContent(
                        mediaUrl = "file:///missing-test-photo.jpg",
                        message =
                            Message(
                                id = "photo-1",
                                user_id = "alice",
                                content = "Photo",
                                timeCreated = 0L,
                                timeEdited = null,
                                isRead = false,
                            ),
                        isEncrypted = false,
                        secureState = null,
                        onPhotoClick = { opened++ },
                        onPhotoLongPress = { actions++ },
                    )
                }
            }
            onNode(hasClickAction()).assertHasClickAction().performClick()
            onNode(hasClickAction()).performSemanticsAction(SemanticsActions.OnLongClick) { it() }
            onNode(hasClickAction()).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            onNode(hasClickAction()).performKeyInput {
                keyDown(Key.Enter)
                keyUp(Key.Enter)
            }
            runOnIdle {
                assertEquals(2, opened)
                assertEquals(1, actions)
            }
        }

    @Test
    fun selectedChipExposesSelection() =
        runComposeUiTest {
            var clicks = 0
            setContent {
                MaterialTheme { ClickChip("Friends", selected = true, onClick = { clicks++ }) }
            }
            onNodeWithText("Friends").assertIsSelected().performClick()
            runOnIdle { assertEquals(1, clicks) }
        }

    @Test
    fun disabledChipDoesNotInvokeAction() =
        runComposeUiTest {
            var clicks = 0
            setContent {
                MaterialTheme { ClickChip("Friends", selected = true, enabled = false, onClick = { clicks++ }) }
            }
            onNodeWithText("Friends").assertIsSelected().assertIsNotEnabled().performClick()
            runOnIdle { assertEquals(0, clicks) }
        }
}
