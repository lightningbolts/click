package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSDateComponents
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformEventTimePickerBody(
    initialHour: Int,
    initialMinute: Int,
    modifier: Modifier,
    onSelectionChange: (hour: Int, minute: Int) -> Unit,
) {
    key(initialHour, initialMinute) {
        UIKitView(
            modifier = modifier
                .fillMaxWidth()
                .height(216.dp),
            factory = {
                UIDatePicker().apply {
                    datePickerMode = UIDatePickerMode.UIDatePickerModeTime
                    preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleWheels
                    setInitialTime(initialHour, initialMinute)
                }
            },
            update = { picker ->
                picker.setInitialTime(initialHour, initialMinute)
            },
        )
        onSelectionChange(initialHour, initialMinute)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun readIosEventTimePickerSelection(
    picker: UIDatePicker?,
    fallbackHour: Int,
    fallbackMinute: Int,
): Pair<Int, Int> {
    val target = picker ?: return fallbackHour to fallbackMinute
    val components = NSCalendar.currentCalendar.components(
        unitFlags = platform.Foundation.NSCalendarUnitHour or platform.Foundation.NSCalendarUnitMinute,
        fromDate = target.date,
    )
    return components.hour.toInt() to components.minute.toInt()
}

@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun BindIosEventTimePickerRef(
    initialHour: Int,
    initialMinute: Int,
    pickerRef: androidx.compose.runtime.MutableState<Any?>,
    modifier: Modifier = Modifier,
) {
    key(initialHour, initialMinute) {
        UIKitView(
            modifier = modifier
                .fillMaxWidth()
                .height(216.dp),
            factory = {
                UIDatePicker().apply {
                    datePickerMode = UIDatePickerMode.UIDatePickerModeTime
                    preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleWheels
                    setInitialTime(initialHour, initialMinute)
                    pickerRef.value = this
                }
            },
            update = { picker ->
                picker.setInitialTime(initialHour, initialMinute)
                pickerRef.value = picker
            },
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun UIDatePicker.setInitialTime(hour: Int, minute: Int) {
    val components = NSDateComponents().apply {
        this.hour = hour.toLong()
        this.minute = minute.toLong()
    }
    NSCalendar.currentCalendar.dateFromComponents(components)?.let { date ->
        setDate(date, animated = false)
    }
}
