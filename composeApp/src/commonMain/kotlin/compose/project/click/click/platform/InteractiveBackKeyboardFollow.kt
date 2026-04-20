package compose.project.click.click.platform

/**
 * Keeps the system keyboard visually aligned with interactive back-swipe.
 * Android: no-op (IME is a separate surface). iOS: translates auxiliary windows that host the keyboard.
 */
expect object InteractiveBackKeyboardFollow {
    /** [progress] is 0..1 for the active back-swipe dismissal gesture. */
    fun setDismissProgress(progress: Float)
    fun reset()
}
