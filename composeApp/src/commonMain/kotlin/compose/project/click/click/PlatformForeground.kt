package compose.project.click.click

import kotlinx.coroutines.flow.StateFlow

/**
 * Increments on [notifyPlatformApplicationForeground] (Activity [onResume]) so Compose can re-read
 * system permission state.
 */
expect fun platformForegroundTickFlow(): StateFlow<Long>

expect fun notifyPlatformApplicationForeground()
