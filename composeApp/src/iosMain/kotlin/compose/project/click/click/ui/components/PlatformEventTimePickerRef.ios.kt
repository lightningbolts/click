package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import platform.UIKit.UIDatePicker

@Composable
internal actual fun EventTimePickerPopupBody(
    initialHour: Int,
    initialMinute: Int,
    pickerRef: androidx.compose.runtime.MutableState<Any?>,
    modifier: Modifier,
    onSelectionChange: (hour: Int, minute: Int) -> Unit,
) {
    BindIosEventTimePickerRef(
        initialHour = initialHour,
        initialMinute = initialMinute,
        pickerRef = pickerRef,
        modifier = modifier,
    )
    onSelectionChange(initialHour, initialMinute)
}

internal actual fun readPlatformEventTimeSelection(
    pickerRef: Any?,
    fallbackHour: Int,
    fallbackMinute: Int,
): Pair<Int, Int> =
    readIosEventTimePickerSelection(pickerRef as? UIDatePicker, fallbackHour, fallbackMinute)
