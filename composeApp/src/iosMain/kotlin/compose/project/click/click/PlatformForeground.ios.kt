package compose.project.click.click

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val iosForegroundTicks = MutableStateFlow(0L)

actual fun platformForegroundTickFlow(): StateFlow<Long> = iosForegroundTicks.asStateFlow()

actual fun notifyPlatformApplicationForeground() {
    iosForegroundTicks.value = iosForegroundTicks.value + 1
}
