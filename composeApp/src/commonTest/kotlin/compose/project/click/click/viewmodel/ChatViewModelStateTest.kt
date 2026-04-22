package compose.project.click.click.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Lightweight multiplatform tests for chat state shapes (no Android Main / ViewModel runtime).
 * Full [ChatViewModel] integration tests live in `androidUnitTest` with Robolectric.
 */
class ChatViewModelStateTest {

    @Test
    fun chatListState_error_exposesMessage() {
        val state = ChatListState.Error("offline")
        assertEquals("offline", state.message)
    }

    @Test
    fun chatMessagesState_loading_isDistinctBranch() {
        val state: ChatMessagesState = ChatMessagesState.Loading
        assertIs<ChatMessagesState.Loading>(state)
    }
}
