package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun EventTimePickerPopupBody(
    initialHour: Int,
    initialMinute: Int,
    pickerRef: androidx.compose.runtime.MutableState<Any?>,
    modifier: Modifier,
    onSelectionChange: (hour: Int, minute: Int) -> Unit,
) {
    PlatformEventTimePickerBody(
        initialHour = initialHour,
        initialMinute = initialMinute,
        modifier = modifier,
        onSelectionChange = onSelectionChange,
    )
}

internal actual fun readPlatformEventTimeSelection(
    pickerRef: Any?,
    fallbackHour: Int,
    fallbackMinute: Int,
): Pair<Int, Int> = fallbackHour to fallbackMinute
