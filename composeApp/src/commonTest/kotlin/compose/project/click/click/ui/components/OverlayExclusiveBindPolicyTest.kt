package compose.project.click.click.ui.components // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OverlayExclusiveBindPolicyTest {
    @Test
    fun mediaCoversOnlyUIKitTabChrome() {
        assertTrue(OverlayExclusiveBindPolicy.shouldCoverNativeTabBarForMedia(isIOS = true))
        assertFalse(OverlayExclusiveBindPolicy.shouldCoverNativeTabBarForMedia(isIOS = false))
    }

    @Test
    fun chatSkipsBindWhileCameraHoldsExclusive() {
        val chat = Any()
        val camera = Any()
        assertTrue(OverlayExclusiveBindPolicy.shouldSkipOverlayBind(camera, chat))
        assertFalse(OverlayExclusiveBindPolicy.shouldSkipOverlayBind(camera, camera))
        assertFalse(OverlayExclusiveBindPolicy.shouldSkipOverlayBind(null, chat))
    }

    @Test
    fun cameraStashesChatOwnerWhenTakingExclusive() {
        val chat = Any()
        val camera = Any()
        assertTrue(
            OverlayExclusiveBindPolicy.shouldStashUnderlyingOwner(
                exclusiveOwner = camera,
                binderOwner = camera,
                currentOwner = chat,
            ),
        )
        assertFalse(
            OverlayExclusiveBindPolicy.shouldStashUnderlyingOwner(
                exclusiveOwner = camera,
                binderOwner = camera,
                currentOwner = camera,
            ),
        )
        assertFalse(
            OverlayExclusiveBindPolicy.shouldStashUnderlyingOwner(
                exclusiveOwner = camera,
                binderOwner = camera,
                currentOwner = null,
            ),
        )
        assertFalse(
            OverlayExclusiveBindPolicy.shouldStashUnderlyingOwner(
                exclusiveOwner = camera,
                binderOwner = chat,
                currentOwner = chat,
            ),
        )
    }

    @Test
    fun exclusiveReleaseKeepsOverlayWhenChatStillBound() {
        assertFalse(OverlayExclusiveBindPolicy.shouldHideOverlayOnExclusiveRelease(otherOverlayBindersRemain = true))
        assertTrue(OverlayExclusiveBindPolicy.shouldHideOverlayOnExclusiveRelease(otherOverlayBindersRemain = false))
    }

    @Test
    fun exclusiveReleaseRestoresStashedChatOwner() {
        val chat = Any()
        val camera = Any()
        assertSame(
            chat,
            OverlayExclusiveBindPolicy.restoredOwnerToken(
                releasingOwner = camera,
                currentOwner = camera,
                underlyingOwner = chat,
            ),
        )
        assertNull(
            OverlayExclusiveBindPolicy.restoredOwnerToken(
                releasingOwner = camera,
                currentOwner = camera,
                underlyingOwner = null,
            ),
        )
        assertSame(
            chat,
            OverlayExclusiveBindPolicy.restoredOwnerToken(
                releasingOwner = camera,
                currentOwner = chat,
                underlyingOwner = chat,
            ),
        )
    }

    @Test
    fun leadingSymbolSwapsOnlyWhenCloseFlagChanges() {
        assertFalse(OverlayExclusiveBindPolicy.shouldReplaceLeadingChromeSymbol(null, true))
        assertFalse(OverlayExclusiveBindPolicy.shouldReplaceLeadingChromeSymbol(true, true))
        assertTrue(OverlayExclusiveBindPolicy.shouldReplaceLeadingChromeSymbol(true, false))
        assertTrue(OverlayExclusiveBindPolicy.shouldReplaceLeadingChromeSymbol(false, true))
    }

    @Test
    fun mediaOverlayKeepsConversationTitleAndAvatar() {
        assertTrue(
            OverlayExclusiveBindPolicy.shouldPreserveConversationChrome(
                leadingClose = true,
                title = "",
                hasIdentity = false,
                hasExistingTitle = true,
            ),
        )
        assertFalse(
            OverlayExclusiveBindPolicy.shouldPreserveConversationChrome(
                leadingClose = true,
                title = "Click Drops",
                hasIdentity = false,
                hasExistingTitle = true,
            ),
        )
        assertFalse(
            OverlayExclusiveBindPolicy.shouldPreserveConversationChrome(
                leadingClose = true,
                title = "",
                hasIdentity = true,
                hasExistingTitle = true,
            ),
        )
        assertFalse(
            OverlayExclusiveBindPolicy.shouldPreserveConversationChrome(
                leadingClose = true,
                title = "",
                hasIdentity = false,
                hasExistingTitle = false,
            ),
        )
    }

    @Test
    fun overlayHideReappliesExpandedTabHeight() {
        assertTrue(OverlayExclusiveBindPolicy.shouldReapplyTabBarHeightOnOverlayHide())
        assertEquals(134.0, NativeHeaderMetrics.barHeightPt(0f, hasSubtitle = true), 0.01)
        assertEquals(52.0, NativeHeaderMetrics.barHeightPt(1f, hasSubtitle = true), 0.01)
        assertEquals(34.0, NativeHeaderMetrics.titlePointSize(0f), 0.01)
    }
}
