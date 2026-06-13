package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import compose.project.click.click.ui.theme.PrimaryBlue
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun PlatformEventDatePickerBody(
    initialEpochMs: Long,
    modifier: Modifier,
    pickerRef: MutableState<Any?>?,
    onSelectionChange: (Long) -> Unit,
) {
    key(initialEpochMs) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialEpochMs)
        LaunchedEffect(pickerState.selectedDateMillis) {
            val selected = pickerState.selectedDateMillis ?: initialEpochMs
            onSelectionChange(selected)
        }
        DatePicker(
            state = pickerState,
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            showModeToggle = false,
            title = null,
            headline = {
                Text(
                    text = formatAndroidPickerDate(pickerState.selectedDateMillis ?: initialEpochMs),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassSheetTokens.OnOled,
                )
            },
            colors = DatePickerDefaults.colors(
                containerColor = GlassSheetTokens.OledBlack,
                titleContentColor = GlassSheetTokens.OnOled,
                headlineContentColor = GlassSheetTokens.OnOled,
                weekdayContentColor = GlassSheetTokens.OnOledMuted,
                subheadContentColor = GlassSheetTokens.OnOledMuted,
                navigationContentColor = GlassSheetTokens.OnOled,
                yearContentColor = GlassSheetTokens.OnOled,
                currentYearContentColor = GlassSheetTokens.OnOled,
                selectedYearContentColor = GlassSheetTokens.OnOled,
                selectedYearContainerColor = PrimaryBlue,
                dayContentColor = GlassSheetTokens.OnOled,
                selectedDayContainerColor = PrimaryBlue,
                selectedDayContentColor = GlassSheetTokens.OnOled,
                todayDateBorderColor = PrimaryBlue,
                todayContentColor = PrimaryBlue,
                dayInSelectionRangeContainerColor = GlassSheetTokens.GlassSurface,
            ),
        )
    }
}

internal actual fun readPlatformEventDateSelection(
    pickerRef: Any?,
    fallbackEpochMs: Long,
): Long = fallbackEpochMs

private fun formatAndroidPickerDate(epochMs: Long): String {
    return Instant.fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()
}
