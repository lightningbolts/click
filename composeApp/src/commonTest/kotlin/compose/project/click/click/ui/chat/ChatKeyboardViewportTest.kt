package compose.project.click.click.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatKeyboardViewportTest {
    @Test
    fun latestTimelineFollowsKeyboardWhenSettled() {
        assertTrue(
            chatTimelineShouldFollowKeyboard(
                firstVisibleItemIndex = 0,
                initialTimelineScrollDone = true,
                userScrollInProgress = false,
            ),
        )
    }

    @Test
    fun historyTimelineDoesNotMoveWithKeyboard() {
        assertFalse(
            chatTimelineShouldFollowKeyboard(
                firstVisibleItemIndex = 3,
                initialTimelineScrollDone = true,
                userScrollInProgress = false,
            ),
        )
    }

    @Test
    fun activeUserScrollWinsOverKeyboardFollow() {
        assertFalse(
            chatTimelineShouldFollowKeyboard(
                firstVisibleItemIndex = 0,
                initialTimelineScrollDone = true,
                userScrollInProgress = true,
            ),
        )
    }

    @Test
    fun emptyNewestTimelineFollowsKeyboardBeforeFirstMessageIsInserted() {
        assertTrue(
            chatTimelineShouldFollowKeyboard(
                firstVisibleItemIndex = 0,
                initialTimelineScrollDone = false,
                userScrollInProgress = false,
            ),
        )
    }

    @Test
    fun keyboardSessionLatchKeepsDecisionUntilKeyboardCloses() {
        val latch = ChatKeyboardSessionLatch()

        assertFalse(latch.update(liftPx = 0f, shouldFollowOnOpen = true))
        assertTrue(latch.update(liftPx = 120f, shouldFollowOnOpen = true))

        // The user can move into history while the keyboard is still open. The session must keep
        // its original latest-message decision instead of dropping the timeline by the IME height.
        assertTrue(latch.update(liftPx = 320f, shouldFollowOnOpen = false))
        assertTrue(latch.update(liftPx = 90f, shouldFollowOnOpen = false))

        assertFalse(latch.update(liftPx = 0f, shouldFollowOnOpen = false))

        // A new keyboard session recomputes the policy from the new focus-time anchor.
        assertFalse(latch.update(liftPx = 150f, shouldFollowOnOpen = false))
    }

    @Test
    fun effectiveKeyboardLiftSubtractsNavigationInsetAndClamps() {
        assertEquals(480, effectiveChatKeyboardLiftPx(imeBottomPx = 520, navigationBottomPx = 40))
        assertEquals(0, effectiveChatKeyboardLiftPx(imeBottomPx = 24, navigationBottomPx = 40))
    }
}
