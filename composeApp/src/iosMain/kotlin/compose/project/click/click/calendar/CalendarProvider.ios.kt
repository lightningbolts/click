package compose.project.click.click.calendar

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.EventKit.EKAuthorizationStatusAuthorized
import platform.EventKit.EKAuthorizationStatusDenied
import platform.EventKit.EKAuthorizationStatusFullAccess
import platform.EventKit.EKAuthorizationStatusRestricted
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventAvailabilityFree
import platform.EventKit.EKEventStore
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
actual class CalendarProvider {

    private val eventStore = EKEventStore()

    actual fun getAccessStatus(): CalendarAccessStatus {
        return mapAuthorizationStatus(
            EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent),
        )
    }

    actual fun requestReadAccess() {
        when (getAccessStatus()) {
            CalendarAccessStatus.Granted -> Unit
            CalendarAccessStatus.NotDetermined -> {
                eventStore.requestFullAccessToEventsWithCompletion { _, _ -> }
            }
            else -> Unit
        }
    }

    actual suspend fun fetchBusyBlocks(
        windowStartEpochMs: Long,
        daysAhead: Int,
    ): CalendarFreeBusy? = withContext(Dispatchers.Default) {
        if (getAccessStatus() != CalendarAccessStatus.Granted) return@withContext null
        val days = daysAhead.coerceIn(1, 14)
        val windowEndEpochMs = windowStartEpochMs + days * 24L * 60L * 60L * 1000L
        val startDate = NSDate.dateWithTimeIntervalSince1970(windowStartEpochMs / 1000.0)
        val endDate = NSDate.dateWithTimeIntervalSince1970(windowEndEpochMs / 1000.0)
        val predicate = eventStore.predicateForEventsWithStartDate(startDate, endDate, null)
            ?: return@withContext null
        val events = eventStore.eventsMatchingPredicate(predicate) as? List<*> ?: emptyList<Any>()
        val busy = events.mapNotNull { raw ->
            val event = raw as? EKEvent ?: return@mapNotNull null
            if (event.availability == EKEventAvailabilityFree) return@mapNotNull null
            val startMs = (event.startDate?.timeIntervalSince1970 ?: return@mapNotNull null) * 1000.0
            val endMs = (event.endDate?.timeIntervalSince1970 ?: return@mapNotNull null) * 1000.0
            val start = startMs.toLong()
            val end = endMs.toLong()
            if (end > start) BusyBlock(startEpochMs = start, endEpochMs = end) else null
        }
        CalendarFreeBusy(
            busyBlocks = mergeBusyBlocks(busy),
            windowStartEpochMs = windowStartEpochMs,
            windowEndEpochMs = windowEndEpochMs,
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun mapAuthorizationStatus(raw: Long): CalendarAccessStatus = when (raw) {
    EKAuthorizationStatusAuthorized,
    EKAuthorizationStatusFullAccess,
    -> CalendarAccessStatus.Granted
    EKAuthorizationStatusDenied -> CalendarAccessStatus.Denied
    EKAuthorizationStatusRestricted -> CalendarAccessStatus.Restricted
    else -> CalendarAccessStatus.NotDetermined
}

/** Suspend until the user responds to the calendar permission sheet (iOS). */
@OptIn(ExperimentalForeignApi::class)
suspend fun awaitIosCalendarReadAccess(): CalendarAccessStatus = suspendCancellableCoroutine { cont ->
    when (mapAuthorizationStatus(EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent))) {
        CalendarAccessStatus.Granted -> cont.resume(CalendarAccessStatus.Granted)
        CalendarAccessStatus.Denied -> cont.resume(CalendarAccessStatus.Denied)
        CalendarAccessStatus.Restricted -> cont.resume(CalendarAccessStatus.Restricted)
        CalendarAccessStatus.NotDetermined -> {
            EKEventStore().requestFullAccessToEventsWithCompletion { granted, _ ->
                if (cont.isActive) {
                    cont.resume(if (granted) CalendarAccessStatus.Granted else CalendarAccessStatus.Denied)
                }
            }
        }
    }
}
