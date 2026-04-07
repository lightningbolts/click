package compose.project.click.click

import kotlinx.coroutines.flow.StateFlow

/**
 * iOS: increments when the app becomes active so Compose can re-read system permission state.
 * Android: inert flow (never ticks).
 */
expect fun platformForegroundTickFlow(): StateFlow<Long>

expect fun notifyPlatformApplicationForeground()
