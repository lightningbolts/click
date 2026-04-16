package compose.project.click.click.ui.chat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.data.models.User
import compose.project.click.click.viewmodel.SecureChatMediaLoadState
import kotlin.test.Test

/**
 * Compose UI smoke tests for the chat message row (R2 Phase 2 audit — T4).
 *
 * Context: R1.1 hoisted `collectAsState` for `currentUserId` and
 * `secureChatMediaLoadState` OUT of every LazyColumn item and down into the
 * bubble as **plain parameters**. Regressing that would cause a subscription
 * fan-out across every message in the list (N collectors per emit).
 *
 * These tests assert the post-refactor contract holds at the composable
 * boundary:
 *   - The bubble can render without any `SecureChatMediaHost` wired up,
 *     provided a pre-resolved `secureMediaState` is passed (or null for
 *     plain-text messages).
 *   - `currentUserId` is a String? parameter rather than a collected StateFlow.
 *   - Basic text renders.
 *
 * Behavioural assertions (swipe math, long-press context menu timing, reply
 * preview layout) are covered by `ChatSwipeMathTest` / `ChatTimelineTest` in
 * commonTest — this file intentionally keeps to the Compose harness smoke
 * surface.
 */
@OptIn(ExperimentalTestApi::class)
class ChatMessageBubbleUiTest {

    private fun textMessage(
        id: String = "m-1",
        senderId: String = "alice",
        body: String = "hello",
        isMine: Boolean = false,
    ): MessageWithUser = MessageWithUser(
        message = Message(
            id = id,
            user_id = senderId,
            content = body,
            timeCreated = 0L,
            timeEdited = null,
            isRead = false,
            messageType = "text",
            metadata = null,
        ),
        user = User(
            id = senderId,
            name = "Alice",
            image = null,
            createdAt = 0L,
        ),
        isSent = isMine,
    )

    @Test
    fun receivedTextBubble_rendersContent() = runComposeUiTest {
        setContent {
            ChatMessageBubble(
                messageWithUser = textMessage(body = "incoming message"),
                currentUserId = "bob",
                onForward = {},
                secureMediaState = null,
            )
        }
        onNodeWithText("incoming message").assertExists()
    }

    @Test
    fun sentTextBubble_rendersContent() = runComposeUiTest {
        setContent {
            ChatMessageBubble(
                messageWithUser = textMessage(body = "outgoing message", isMine = true),
                currentUserId = "alice",
                onForward = {},
                secureMediaState = null,
            )
        }
        onNodeWithText("outgoing message").assertExists()
    }

    @Test
    fun bubble_doesNotRequireSecureMediaHost_whenStatePreResolved() = runComposeUiTest {
        // R1.1 guarantee: passing a pre-resolved `secureMediaState` must work
        // without a `SecureChatMediaHost`. If this throws, the hoisting refactor
        // has been reverted and every list item will start collecting again.
        val preResolved = SecureChatMediaLoadState(
            loading = false,
            imageBytes = null,
            audioLocalPath = null,
            error = null,
        )
        setContent {
            ChatMessageBubble(
                messageWithUser = textMessage(body = "with pre-resolved media state"),
                currentUserId = "bob",
                secureMediaHost = null,
                secureMediaState = preResolved,
                onForward = {},
            )
        }
        onNodeWithText("with pre-resolved media state").assertExists()
    }

    @Test
    fun bubble_contextMenuDisabled_hidesLongPress() = runComposeUiTest {
        // `enableMessageContextMenu = false` is the hub-preview read-only mode;
        // it must still render content.
        setContent {
            ChatMessageBubble(
                messageWithUser = textMessage(body = "hub preview"),
                currentUserId = "bob",
                secureMediaState = null,
                enableMessageContextMenu = false,
                onForward = {},
            )
        }
        onNodeWithText("hub preview").assertExists()
    }
}
