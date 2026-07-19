package compose.project.click.click.ui.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatInteractionPolicyTest {

    @Test
    fun inboundOnlyFollowsWhenInitialPositioningFinishedAndReaderIsNearBottom() {
        assertFalse(chatTimelineShouldFollowInbound(firstVisibleItemIndex = 0, initialTimelineScrollDone = false))
        assertTrue(chatTimelineShouldFollowInbound(firstVisibleItemIndex = 0, initialTimelineScrollDone = true))
        assertTrue(chatTimelineShouldFollowInbound(firstVisibleItemIndex = 2, initialTimelineScrollDone = true))
        assertFalse(chatTimelineShouldFollowInbound(firstVisibleItemIndex = 3, initialTimelineScrollDone = true))
        assertFalse(chatTimelineShouldFollowInbound(firstVisibleItemIndex = 50, initialTimelineScrollDone = true))
    }

    @Test
    fun composerSubmitPolicyRejectsBlankDisabledSendingAndGuardedStates() {
        assertTrue(
            chatComposerCanSubmit(
                value = "hello",
                enabled = true,
                externallySending = false,
                submitGuarded = false,
            ),
        )
        assertFalse(chatComposerCanSubmit("   ", true, false, false))
        assertFalse(chatComposerCanSubmit("hello", false, false, false))
        assertFalse(chatComposerCanSubmit("hello", true, true, false))
        assertFalse(chatComposerCanSubmit("hello", true, false, true))
    }
}
