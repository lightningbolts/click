package compose.project.click.click.ui.components // pragma: allowlist secret

/**
 * Click Drops (and similar sheets) rebind the overlay navigation bar while a conversation
 * overlay is still composed. The camera bind is exclusive: chat must not overwrite chrome, and
 * dismissing the camera must not release/hide the overlay (that leaked the tab-root large title).
 * Chat re-applies once exclusive ownership clears.
 */
object OverlayExclusiveBindPolicy {
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
}
