package compose.project.click.click.ui.components

import click.ios.picker.ClickLayoutFullWidthDatePicker
import click.ios.picker.ClickLayoutFullWidthTimePicker
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIDatePicker

@OptIn(ExperimentalForeignApi::class)
internal fun configureIosDatePicker(picker: UIDatePicker) {
    ClickLayoutFullWidthDatePicker(picker)
}

@OptIn(ExperimentalForeignApi::class)
internal fun configureIosTimePicker(picker: UIDatePicker) {
    ClickLayoutFullWidthTimePicker(picker)
}

@OptIn(ExperimentalForeignApi::class)
internal fun readIosDatePickerEpochMs(picker: UIDatePicker?, fallbackEpochMs: Long): Long {
    val date = picker?.date ?: return fallbackEpochMs
    return iosNSDateToLocalStartOfDayEpochMs(date)
}

@OptIn(ExperimentalForeignApi::class)
internal fun iosNSDateToLocalStartOfDayEpochMs(date: NSDate): Long {
    val calendar = NSCalendar.currentCalendar
    calendar.timeZone = NSTimeZone.localTimeZone
    val components = calendar.components(
        unitFlags = platform.Foundation.NSCalendarUnitYear or
            platform.Foundation.NSCalendarUnitMonth or
            platform.Foundation.NSCalendarUnitDay,
        fromDate = date,
    )
    val startOfDay = calendar.dateFromComponents(
        NSDateComponents().apply {
            year = components.year
            month = components.month
            day = components.day
            hour = 0
            minute = 0
            second = 0
        },
    ) ?: return (date.timeIntervalSince1970 * 1000.0).toLong()
    return (startOfDay.timeIntervalSince1970 * 1000.0).toLong()
}

@OptIn(ExperimentalForeignApi::class)
internal fun iosEpochMsToNSDate(epochMs: Long): NSDate {
    return NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0)
}
