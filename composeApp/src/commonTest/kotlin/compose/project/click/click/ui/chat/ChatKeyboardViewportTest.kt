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
    fun timelineDoesNotFollowBeforeInitialAnchorIsEstablished() {
        assertFalse(
            chatTimelineShouldFollowKeyboard(
                firstVisibleItemIndex = 0,
                initialTimelineScrollDone = false,
                userScrollInProgress = false,
            ),
        )
    }

    @Test
    fun effectiveKeyboardLiftSubtractsNavigationInsetAndClamps() {
        assertEquals(480, effectiveChatKeyboardLiftPx(imeBottomPx = 520, navigationBottomPx = 40))
        assertEquals(0, effectiveChatKeyboardLiftPx(imeBottomPx = 24, navigationBottomPx = 40))
    }
}
