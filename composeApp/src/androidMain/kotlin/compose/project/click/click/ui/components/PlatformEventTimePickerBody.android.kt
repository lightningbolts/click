package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.PrimaryBlue

private val EventTimePickerHeight = 220.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun PlatformEventTimePickerBody(
    initialHour: Int,
    initialMinute: Int,
    modifier: Modifier,
    onSelectionChange: (hour: Int, minute: Int) -> Unit,
) {
    key(initialHour, initialMinute) {
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = false,
        )
        LaunchedEffect(timePickerState.hour, timePickerState.minute) {
            onSelectionChange(timePickerState.hour, timePickerState.minute)
        }
        TimePicker(
            state = timePickerState,
            modifier = modifier
                .fillMaxWidth()
                .height(EventTimePickerHeight)
                .wrapContentHeight(),
            colors = TimePickerDefaults.colors(
                clockDialColor = GlassSheetTokens.GlassSurface,
                clockDialSelectedContentColor = GlassSheetTokens.OnOled,
                clockDialUnselectedContentColor = GlassSheetTokens.OnOledMuted,
                selectorColor = PrimaryBlue,
                containerColor = GlassSheetTokens.OledBlack,
                periodSelectorBorderColor = GlassSheetTokens.GlassBorder,
                periodSelectorSelectedContainerColor = PrimaryBlue,
                periodSelectorUnselectedContainerColor = GlassSheetTokens.GlassSurface,
                periodSelectorSelectedContentColor = GlassSheetTokens.OnOled,
                periodSelectorUnselectedContentColor = GlassSheetTokens.OnOledMuted,
                timeSelectorSelectedContainerColor = PrimaryBlue,
                timeSelectorUnselectedContainerColor = GlassSheetTokens.GlassSurface,
                timeSelectorSelectedContentColor = GlassSheetTokens.OnOled,
                timeSelectorUnselectedContentColor = GlassSheetTokens.OnOledMuted,
            ),
        )
    }
}
