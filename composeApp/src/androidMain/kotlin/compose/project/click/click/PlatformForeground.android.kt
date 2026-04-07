package compose.project.click.click

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val androidForegroundTicks = MutableStateFlow(0L)

actual fun platformForegroundTickFlow(): StateFlow<Long> = androidForegroundTicks.asStateFlow()

actual fun notifyPlatformApplicationForeground() {
    // No-op: Android uses runtime permission callbacks / Activity resume.
}
