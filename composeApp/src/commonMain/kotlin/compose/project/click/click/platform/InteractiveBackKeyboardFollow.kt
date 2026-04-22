package compose.project.click.click.platform

/**
 * Reserved hook for native keyboard / interactive-back coordination.
 * All targets are currently **no-ops**: mutating keyboard UIKit windows on iOS was destabilizing
 * UIKit’s keyboard dismiss animations across sessions; Compose handles the sliding surface.
 */
expect object InteractiveBackKeyboardFollow {
    fun setSwipeTranslationX(translationXPx: Float)
    fun reset()
}
