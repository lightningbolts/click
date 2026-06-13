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
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle

private val EventDateInlineHeight = 320.dp

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformEventDatePickerBody(
    initialEpochMs: Long,
    modifier: Modifier,
    pickerRef: MutableState<Any?>?,
    onSelectionChange: (Long) -> Unit,
) {
    key(initialEpochMs) {
        UIKitView(
            modifier = modifier
                .fillMaxWidth()
                .height(EventDateInlineHeight),
            factory = {
                UIDatePicker().apply {
                    datePickerMode = UIDatePickerMode.UIDatePickerModeDate
                    preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleInline
                    date = iosEpochMsToNSDate(initialEpochMs)
                    configureIosDatePicker(this)
                    pickerRef?.value = this
                }
            },
            update = { picker ->
                pickerRef?.value = picker
            },
            onRelease = {
                if (pickerRef?.value === it) {
                    pickerRef.value = null
                }
            },
        )
    }
}

internal actual fun readPlatformEventDateSelection(
    pickerRef: Any?,
    fallbackEpochMs: Long,
): Long = readIosDatePickerEpochMs(pickerRef as? UIDatePicker, fallbackEpochMs)
