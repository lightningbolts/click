package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun EventTimePickerPopupBody(
    initialHour: Int,
    initialMinute: Int,
    pickerRef: androidx.compose.runtime.MutableState<Any?>,
    modifier: Modifier = Modifier,
    onSelectionChange: (hour: Int, minute: Int) -> Unit,
)

internal expect fun readPlatformEventTimeSelection(
    pickerRef: Any?,
    fallbackHour: Int,
    fallbackMinute: Int,
): Pair<Int, Int>
