@file:OptIn(kotlin.time.ExperimentalTime::class)

package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import compose.project.click.click.events.EventSchedule
import compose.project.click.click.events.EventScheduleValidationError
import compose.project.click.click.events.validateEventSchedule
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDateTimePicker(
    schedule: EventSchedule,
    onScheduleChange: (EventSchedule) -> Unit,
    validationError: EventScheduleValidationError?,
    modifier: Modifier = Modifier,
) {
    var pickingStart by remember { mutableStateOf(true) }
    var showDatePicker by remember { mutableStateOf(false) }

    val startLocal = remember(schedule.startEpochMs) {
        Instant.fromEpochMilliseconds(schedule.startEpochMs).toLocalDateTime(TimeZone.currentSystemDefault())
    }
    val endLocal = remember(schedule.endEpochMs) {
        Instant.fromEpochMilliseconds(schedule.endEpochMs).toLocalDateTime(TimeZone.currentSystemDefault())
    }

    var startHour by remember(schedule.startEpochMs) { mutableIntStateOf(startLocal.hour) }
    var startMinute by remember(schedule.startEpochMs) { mutableIntStateOf(startLocal.minute) }
    var endHour by remember(schedule.endEpochMs) { mutableIntStateOf(endLocal.hour) }
    var endMinute by remember(schedule.endEpochMs) { mutableIntStateOf(endLocal.minute) }

    fun applySchedule(startMs: Long, endMs: Long) {
        onScheduleChange(EventSchedule(startEpochMs = startMs, endEpochMs = endMs))
    }

    fun mergeDateTime(dateMs: Long, hour: Int, minute: Int): Long {
        val dayStart = Instant.fromEpochMilliseconds(dateMs)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        return dayStart + (hour.coerceIn(0, 23) * 60L + minute.coerceIn(0, 59)) * 60_000L
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Event schedule",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Pick start and end date/time (max 1 month).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pickingStart = true; showDatePicker = true }, modifier = Modifier.weight(1f)) {
                Text("Start: ${startLocal.date}")
            }
            Button(onClick = { pickingStart = false; showDatePicker = true }, modifier = Modifier.weight(1f)) {
                Text("End: ${endLocal.date}")
            }
        }
        TimeRow(label = "Start time", hour = startHour, minute = startMinute) { h, m ->
            startHour = h
            startMinute = m
            applySchedule(
                mergeDateTime(schedule.startEpochMs, h, m),
                schedule.endEpochMs,
            )
        }
        TimeRow(label = "End time", hour = endHour, minute = endMinute) { h, m ->
            endHour = h
            endMinute = m
            applySchedule(
                schedule.startEpochMs,
                mergeDateTime(schedule.endEpochMs, h, m),
            )
        }
        validationError?.let { err ->
            Text(
                text = when (err) {
                    EventScheduleValidationError.EndBeforeStart -> "End must be after start."
                    EventScheduleValidationError.StartInPast -> "Start time must be in the future."
                    EventScheduleValidationError.DurationExceedsOneMonth -> "Events can last at most 1 month."
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (showDatePicker) {
        val initialMs = if (pickingStart) schedule.startEpochMs else schedule.endEpochMs
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selected = pickerState.selectedDateMillis ?: initialMs
                    if (pickingStart) {
                        applySchedule(
                            mergeDateTime(selected, startHour, startMinute),
                            schedule.endEpochMs,
                        )
                    } else {
                        applySchedule(
                            schedule.startEpochMs,
                            mergeDateTime(selected, endHour, endMinute),
                        )
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun TimeRow(
    label: String,
    hour: Int,
    minute: Int,
    onChange: (hour: Int, minute: Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = hour.toString().padStart(2, '0'),
            onValueChange = { raw ->
                raw.toIntOrNull()?.let { onChange(it.coerceIn(0, 23), minute) }
            },
            modifier = Modifier.weight(1f),
            label = { Text("$label hour") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        OutlinedTextField(
            value = minute.toString().padStart(2, '0'),
            onValueChange = { raw ->
                raw.toIntOrNull()?.let { onChange(hour, it.coerceIn(0, 59)) }
            },
            modifier = Modifier.weight(1f),
            label = { Text("$label min") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
    }
}

/** Default event schedule: starts in 1 hour, ends 2 hours later. */
fun defaultEventSchedule(nowEpochMs: Long = Clock.System.now().toEpochMilliseconds()): EventSchedule {
    val start = nowEpochMs + 60L * 60_000L
    val end = start + 2L * 60L * 60_000L
    return EventSchedule(startEpochMs = start, endEpochMs = end)
}
