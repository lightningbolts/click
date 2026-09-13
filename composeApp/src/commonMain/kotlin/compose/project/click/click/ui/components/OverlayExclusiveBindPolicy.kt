package compose.project.click.click.ui.components // pragma: allowlist secret

/**
 * Click Drops (and similar sheets) rebind the overlay navigation bar while a conversation
 * overlay is still composed. The camera bind is exclusive: chat must not overwrite chrome, and
 * dismissing the camera must not release/hide the overlay (that leaked the tab-root large title).
 * Chat re-applies once exclusive ownership clears.
 */
object OverlayExclusiveBindPolicy {
    /** UIKit tab chrome needs an explicit cover; Android's Compose bar remains under the lightbox. */
    fun shouldCoverNativeTabBarForMedia(isIOS: Boolean): Boolean = isIOS

    fun shouldSkipOverlayBind(
        exclusiveOwner: Any?,
        binderOwner: Any,
    ): Boolean = exclusiveOwner != null && exclusiveOwner !== binderOwner

    fun shouldStashUnderlyingOwner(
        exclusiveOwner: Any?,
        binderOwner: Any,
        currentOwner: Any?,
    ): Boolean = exclusiveOwner === binderOwner && currentOwner != null && currentOwner !== binderOwner

    /**
     * Exclusive overlay (Click Drops) must keep the bar visible when the conversation bind is
     * still composed. Hide only when this was the last overlay binder.
     */
    fun shouldHideOverlayOnExclusiveRelease(otherOverlayBindersRemain: Boolean): Boolean = !otherOverlayBindersRemain

    fun restoredOwnerToken(
        releasingOwner: Any,
        currentOwner: Any?,
        underlyingOwner: Any?,
    ): Any? {
        if (currentOwner !== releasingOwner) return currentOwner
        return underlyingOwner
    }

    /**
     * Overlay hide after a cover/exclusive dance can leave the tab bar's height constraint at
     * compact 52pt while the large title is still expanded — the subtitle clips to descenders.
     * Re-apply the last expanded metrics instead of trusting the leftover constraint.
     */
    fun shouldReapplyTabBarHeightOnOverlayHide(): Boolean = true

    /**
     * xmark ↔ chevron.backward must swap on the same glass `UIButton`. A null previous
     * value is the first paint (no swap). Callers must not wrap that control in
     * `UIView.transition` — snapshotting Liquid Glass is the blink.
     */
    fun shouldReplaceLeadingChromeSymbol(
        previousLeadingClose: Boolean?,
        nextLeadingClose: Boolean,
    ): Boolean = previousLeadingClose != null && previousLeadingClose != nextLeadingClose

    /**
     * Photo / media lightboxes rebind overlay chrome with an empty title so they can own
     * close + save/share. Wiping the conversation title and avatar is a second chrome
     * rebuild next to the buttons. Keep the existing identity row and only retarget actions.
     */
    fun shouldPreserveConversationChrome(
        leadingClose: Boolean,
        title: String,
        hasIdentity: Boolean,
        hasExistingTitle: Boolean,
    ): Boolean = leadingClose && title.isEmpty() && !hasIdentity && hasExistingTitle
}
