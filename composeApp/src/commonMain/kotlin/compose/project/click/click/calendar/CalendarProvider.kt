package compose.project.click.click.calendar

/**
 * Platform calendar access for read-only free/busy extraction.
 *
 * Android: [android.provider.CalendarContract]
 */
expect class CalendarProvider() {
    fun getAccessStatus(): CalendarAccessStatus

    /** Triggers the OS permission prompt when status is [CalendarAccessStatus.NotDetermined]. */
    fun requestReadAccess()

    /**
     * Returns busy blocks for the next [daysAhead] days starting at [windowStartEpochMs].
     * Returns null when permission is denied or the calendar is unavailable.
     */
    suspend fun fetchBusyBlocks(
        windowStartEpochMs: Long,
        daysAhead: Int = 7,
    ): CalendarFreeBusy?

    /** Visible calendars on this device (names only); empty without permission. */
    suspend fun listCalendars(): List<DeviceCalendar>
}
