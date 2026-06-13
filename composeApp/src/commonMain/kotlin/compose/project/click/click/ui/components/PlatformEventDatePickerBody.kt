package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier

/** Native calendar / date body for event schedule pickers. */
@Composable
expect fun PlatformEventDatePickerBody(
    initialEpochMs: Long,
    modifier: Modifier = Modifier,
    pickerRef: MutableState<Any?>? = null,
    onSelectionChange: (Long) -> Unit,
)

internal expect fun readPlatformEventDateSelection(
    pickerRef: Any?,
    fallbackEpochMs: Long,
): Long
