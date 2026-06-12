package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Native time wheel / clock body for [UnifiedPopupFormDialog] (start/end time). */
@Composable
expect fun PlatformEventTimePickerBody(
    initialHour: Int,
    initialMinute: Int,
    modifier: Modifier = Modifier,
    onSelectionChange: (hour: Int, minute: Int) -> Unit,
)
