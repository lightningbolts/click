@file:OptIn(kotlin.time.ExperimentalTime::class, ExperimentalMaterial3Api::class)

package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.events.EventSchedule
import compose.project.click.click.events.EventScheduleValidationError
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

private val PickerPopupSurfacePadding = 0.dp
private val PickerPopupChromePadding = 16.dp
private val PickerPopupInnerVerticalPadding = 12.dp

@Composable
fun EventDateTimePicker(
    schedule: EventSchedule,
    onScheduleChange: (EventSchedule) -> Unit,
    validationError: EventScheduleValidationError?,
    modifier: Modifier = Modifier,
) {
    var pickingStartDate by remember { mutableStateOf(true) }
    var pickingStartTime by remember { mutableStateOf(true) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

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

    var pendingHour by remember { mutableIntStateOf(startHour) }
    var pendingMinute by remember { mutableIntStateOf(startMinute) }
    var pendingDateMs by remember { mutableLongStateOf(schedule.startEpochMs) }
    val iosTimePickerRef = remember { mutableStateOf<Any?>(null) }
    val iosDatePickerRef = remember { mutableStateOf<Any?>(null) }

    val pickerDialogTitleStyle = MaterialTheme.typography.titleSmall

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
            Button(onClick = { pickingStartDate = true; showDatePicker = true }, modifier = Modifier.weight(1f)) {
                Text("Start: ${startLocal.date}")
            }
            Button(onClick = { pickingStartDate = false; showDatePicker = true }, modifier = Modifier.weight(1f)) {
                Text("End: ${endLocal.date}")
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    pickingStartTime = true
                    pendingHour = startHour
                    pendingMinute = startMinute
                    showTimePicker = true
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Start: ${formatEventTime(startHour, startMinute)}")
            }
            Button(
                onClick = {
                    pickingStartTime = false
                    pendingHour = endHour
                    pendingMinute = endMinute
                    showTimePicker = true
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("End: ${formatEventTime(endHour, endMinute)}")
            }
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

    val dateInitialMs = if (pickingStartDate) schedule.startEpochMs else schedule.endEpochMs
    LaunchedEffect(showDatePicker, dateInitialMs) {
        if (showDatePicker) {
            pendingDateMs = dateInitialMs
        }
    }

    UnifiedPopupFormDialog(
        visible = showDatePicker,
        onDismissRequest = { showDatePicker = false },
        title = if (pickingStartDate) "Start date" else "End date",
        titleStyle = pickerDialogTitleStyle,
        contentMaxWidth = null,
        surfaceHorizontalPadding = PickerPopupSurfacePadding,
        innerPadding = PickerPopupInnerVerticalPadding,
        innerHorizontalPadding = PickerPopupChromePadding,
        bodyHorizontalPadding = 0.dp,
        motion = UnifiedPopupMotion.Picker,
        confirmLabel = "OK",
        onConfirm = {
            val selected = readPlatformEventDateSelection(
                pickerRef = iosDatePickerRef.value,
                fallbackEpochMs = pendingDateMs,
            )
            if (pickingStartDate) {
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
        },
        body = {
            if (showDatePicker) {
                PlatformEventDatePickerBody(
                    initialEpochMs = dateInitialMs,
                    pickerRef = iosDatePickerRef,
                    modifier = Modifier.fillMaxWidth(),
                    onSelectionChange = { pendingDateMs = it },
                )
            }
        },
    )

    UnifiedPopupFormDialog(
        visible = showTimePicker,
        onDismissRequest = { showTimePicker = false },
        title = if (pickingStartTime) "Start time" else "End time",
        titleStyle = pickerDialogTitleStyle,
        contentMaxWidth = null,
        surfaceHorizontalPadding = PickerPopupSurfacePadding,
        innerPadding = PickerPopupInnerVerticalPadding,
        innerHorizontalPadding = PickerPopupChromePadding,
        bodyHorizontalPadding = 0.dp,
        motion = UnifiedPopupMotion.Picker,
        confirmLabel = "OK",
        onConfirm = {
            val (hour, minute) = readPlatformEventTimeSelection(
                pickerRef = iosTimePickerRef.value,
                fallbackHour = pendingHour,
                fallbackMinute = pendingMinute,
            )
            if (pickingStartTime) {
                startHour = hour
                startMinute = minute
                applySchedule(
                    mergeDateTime(schedule.startEpochMs, hour, minute),
                    schedule.endEpochMs,
                )
            } else {
                endHour = hour
                endMinute = minute
                applySchedule(
                    schedule.startEpochMs,
                    mergeDateTime(schedule.endEpochMs, hour, minute),
                )
            }
        },
        body = {
            if (showTimePicker) {
                EventTimePickerPopupBody(
                    initialHour = pendingHour,
                    initialMinute = pendingMinute,
                    pickerRef = iosTimePickerRef,
                    modifier = Modifier.fillMaxWidth(),
                    onSelectionChange = { hour, minute ->
                        pendingHour = hour
                        pendingMinute = minute
                    },
                )
            }
        },
    )
}

private fun formatEventTime(hour: Int, minute: Int): String {
    val h = hour.coerceIn(0, 23)
    val m = minute.coerceIn(0, 59)
    val period = if (h < 12) "AM" else "PM"
    val hour12 = when (val mod = h % 12) {
        0 -> 12
        else -> mod
    }
    return "$hour12:${m.toString().padStart(2, '0')} $period"
}
