package compose.project.click.click.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import compose.project.click.click.calendar.CalendarProvider
import compose.project.click.click.calendar.awaitIosCalendarReadAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
actual fun rememberCalendarPermissionRequester(): ((onComplete: () -> Unit) -> Unit) {
    val provider = remember { CalendarProvider() }
    return { onComplete ->
        when (provider.getAccessStatus()) {
            compose.project.click.click.calendar.CalendarAccessStatus.Granted -> onComplete()
            compose.project.click.click.calendar.CalendarAccessStatus.Denied,
            compose.project.click.click.calendar.CalendarAccessStatus.Restricted,
            -> {
                openApplicationSystemSettings()
                onComplete()
            }
            else -> {
                CoroutineScope(Dispatchers.Main).launch {
                    awaitIosCalendarReadAccess()
                    onComplete()
                }
            }
        }
    }
}
