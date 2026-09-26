@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Material date picker selections are UTC midnights; convert to/from local calendar dates. */
private fun LocalDate.toPickerMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun pickerMillisToDate(ms: Long): LocalDate = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date

/**
 * Two-step date then time picker (Material 3). Dates outside [minEpochMs]..[maxEpochMs] (local
 * days) are disabled; the combined result is clamped into the same range before [onPicked].
 */
@Composable
fun ClickDateTimePickerDialog(
    initialEpochMs: Long,
    minEpochMs: Long,
    maxEpochMs: Long,
    onPicked: (Long) -> Unit,
    onDismiss: () -> Unit,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val initial = Instant.fromEpochMilliseconds(initialEpochMs).toLocalDateTime(timeZone)
    val minDate = Instant.fromEpochMilliseconds(minEpochMs).toLocalDateTime(timeZone).date
    val maxDate = Instant.fromEpochMilliseconds(maxEpochMs).toLocalDateTime(timeZone).date
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    val date = pickedDate
    if (date == null) {
        val dateState =
            rememberDatePickerState(
                initialSelectedDateMillis = initial.date.toPickerMillis(),
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean = pickerMillisToDate(utcTimeMillis) in minDate..maxDate

                        override fun isSelectableYear(year: Int): Boolean = year in minDate.year..maxDate.year
                    },
            )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    enabled = dateState.selectedDateMillis != null,
                    onClick = { dateState.selectedDateMillis?.let { pickedDate = pickerMillisToDate(it) } },
                ) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val timeState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val picked =
                        LocalDateTime(date, LocalTime(timeState.hour, timeState.minute))
                            .toInstant(timeZone)
                            .toEpochMilliseconds()
                    onPicked(picked.coerceIn(minEpochMs, maxEpochMs))
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { pickedDate = null }) { Text("Back") } },
            text = { TimePicker(state = timeState) },
        )
    }
}
