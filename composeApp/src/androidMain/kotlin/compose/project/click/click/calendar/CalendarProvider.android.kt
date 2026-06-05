package compose.project.click.click.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class CalendarProvider {

    private val context: Context
        get() = calendarContext
            ?: throw IllegalStateException("CalendarProvider not initialized. Call initCalendarProvider() from MainActivity first.")

    actual fun getAccessStatus(): CalendarAccessStatus {
        return when (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)) {
            PackageManager.PERMISSION_GRANTED -> CalendarAccessStatus.Granted
            else -> CalendarAccessStatus.NotDetermined
        }
    }

    actual fun requestReadAccess() {
        // Runtime prompt is driven by [rememberCalendarPermissionRequester].
    }

    actual suspend fun fetchBusyBlocks(
        windowStartEpochMs: Long,
        daysAhead: Int,
    ): CalendarFreeBusy? = withContext(Dispatchers.IO) {
        if (getAccessStatus() != CalendarAccessStatus.Granted) return@withContext null
        val days = daysAhead.coerceIn(1, 14)
        val windowEndEpochMs = windowStartEpochMs + days * 24L * 60L * 60L * 1000L
        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.AVAILABILITY,
        )
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, windowStartEpochMs)
        ContentUris.appendId(builder, windowEndEpochMs)
        val busy = ArrayList<BusyBlock>()
        context.contentResolver.query(
            builder.build(),
            projection,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
            val availabilityIdx = cursor.getColumnIndex(CalendarContract.Instances.AVAILABILITY)
            while (cursor.moveToNext()) {
                if (beginIdx < 0 || endIdx < 0) continue
                val availability = if (availabilityIdx >= 0) cursor.getInt(availabilityIdx) else CalendarContract.Events.AVAILABILITY_BUSY
                if (availability == CalendarContract.Events.AVAILABILITY_FREE) continue
                val start = cursor.getLong(beginIdx)
                val end = cursor.getLong(endIdx)
                if (end > start) {
                    busy.add(BusyBlock(startEpochMs = start, endEpochMs = end))
                }
            }
        }
        CalendarFreeBusy(
            busyBlocks = mergeBusyBlocks(busy),
            windowStartEpochMs = windowStartEpochMs,
            windowEndEpochMs = windowEndEpochMs,
        )
    }
}

private var calendarContext: Context? = null

fun initCalendarProvider(context: Context) {
    calendarContext = context.applicationContext
}
