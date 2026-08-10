@file:OptIn(kotlin.time.ExperimentalTime::class, ExperimentalMaterial3Api::class)

package compose.project.click.click.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.events.EventClock12h
import compose.project.click.click.events.EventSchedule
import compose.project.click.click.events.EventScheduleValidationError
import compose.project.click.click.events.MAX_EVENT_DURATION_MS
import compose.project.click.click.events.coerceSameDayEventTimes
import compose.project.click.click.events.eventClock12hFrom24h
import compose.project.click.click.events.eventClock12hTo24h
import compose.project.click.click.events.formatEventClockLabel
import compose.project.click.click.events.formatEventDateOnlyLabel
import compose.project.click.click.events.formatEventDateRangeLabel
import compose.project.click.click.events.localDateToUtcMidnightMillis
import compose.project.click.click.events.mergeLocalDateWithClock
import compose.project.click.click.events.utcMidnightMillisToLocalDate
import compose.project.click.click.ui.theme.PrimaryBlue
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val PickerPopupSurfacePadding = 0.dp
private val PickerPopupChromePadding = 16.dp
private val PickerPopupInnerVerticalPadding = 12.dp
/** DateRangePicker embeds a vertical grid; unbounded max height in a dialog Column crashes. */
private val DateRangePickerHeight = 440.dp
private val TumblerRowHeight = 36.dp
private val TumblerVisibleRows = 5

/**
 * Shared open-state for schedule fields (in scroll) vs dialogs (sibling outside scroll).
 * Keeps UnifiedPopupFormDialog out of verticalScroll measurement.
 */
class EventSchedulePickerUiState {
    var showDatePicker by mutableStateOf(false)
        internal set
    var showTimePicker by mutableStateOf(false)
        internal set
    var pickingStartTime by mutableStateOf(true)
        internal set
    /** When true, open the range picker with only start selected so the next tap sets end. */
    var datePickerFocusEnd by mutableStateOf(false)
        internal set
    var pendingHour12 by mutableIntStateOf(1)
        internal set
    var pendingMinute by mutableIntStateOf(0)
        internal set
    var pendingIsPm by mutableStateOf(false)
        internal set

    fun openDatePicker(focusEnd: Boolean = false) {
        datePickerFocusEnd = focusEnd
        showDatePicker = true
    }

    fun openTimePicker(forStart: Boolean, hour12: Int, minute: Int, isPm: Boolean) {
        pickingStartTime = forStart
        pendingHour12 = hour12
        pendingMinute = minute
        pendingIsPm = isPm
        showTimePicker = true
    }
}

@Composable
fun rememberEventSchedulePickerUiState(): EventSchedulePickerUiState =
    remember { EventSchedulePickerUiState() }

/**
 * Schedule field buttons only — safe to place under [verticalScroll].
 * Pair with [EventSchedulePickerDialogs] as a sibling outside the scroll column.
 */
@Composable
fun EventDateTimePicker(
    schedule: EventSchedule,
    onScheduleChange: (EventSchedule) -> Unit,
    validationError: EventScheduleValidationError?,
    modifier: Modifier = Modifier,
    uiState: EventSchedulePickerUiState = rememberEventSchedulePickerUiState(),
    /** When true, also compose dialogs here (legacy). Prefer false + [EventSchedulePickerDialogs]. */
    includeDialogs: Boolean = false,
) {
    val tz = TimeZone.currentSystemDefault()
    val startLocal = remember(schedule.startEpochMs) {
        Instant.fromEpochMilliseconds(schedule.startEpochMs).toLocalDateTime(tz)
    }
    val endLocal = remember(schedule.endEpochMs) {
        Instant.fromEpochMilliseconds(schedule.endEpochMs).toLocalDateTime(tz)
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
            ScheduleActionButton(
                text = "Start: ${formatEventDateOnlyLabel(schedule.startEpochMs, tz)}",
                onClick = { uiState.openDatePicker(focusEnd = false) },
                modifier = Modifier.weight(1f),
            )
            ScheduleActionButton(
                text = "End: ${formatEventDateOnlyLabel(schedule.endEpochMs, tz)}",
                onClick = { uiState.openDatePicker(focusEnd = true) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScheduleActionButton(
                text = "Start: ${formatEventClockLabel(startLocal.hour, startLocal.minute)}",
                onClick = {
                    val clock = eventClock12hFrom24h(startLocal.hour, startLocal.minute)
                    uiState.openTimePicker(
                        forStart = true,
                        hour12 = clock.hour12,
                        minute = clock.minute,
                        isPm = clock.isPm,
                    )
                },
                modifier = Modifier.weight(1f),
            )
            ScheduleActionButton(
                text = "End: ${formatEventClockLabel(endLocal.hour, endLocal.minute)}",
                onClick = {
                    val clock = eventClock12hFrom24h(endLocal.hour, endLocal.minute)
                    uiState.openTimePicker(
                        forStart = false,
                        hour12 = clock.hour12,
                        minute = clock.minute,
                        isPm = clock.isPm,
                    )
                },
                modifier = Modifier.weight(1f),
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

    if (includeDialogs) {
        EventSchedulePickerDialogs(
            schedule = schedule,
            onScheduleChange = onScheduleChange,
            uiState = uiState,
        )
    }
}

/** Date/time popups — compose as a sibling of the form [verticalScroll], not inside it. */
@Composable
fun EventSchedulePickerDialogs(
    schedule: EventSchedule,
    onScheduleChange: (EventSchedule) -> Unit,
    uiState: EventSchedulePickerUiState,
) {
    val tz = TimeZone.currentSystemDefault()
    val startLocal = remember(schedule.startEpochMs) {
        Instant.fromEpochMilliseconds(schedule.startEpochMs).toLocalDateTime(tz)
    }
    val endLocal = remember(schedule.endEpochMs) {
        Instant.fromEpochMilliseconds(schedule.endEpochMs).toLocalDateTime(tz)
    }
    val sameDay = startLocal.date == endLocal.date
    val pickerDialogTitleStyle = MaterialTheme.typography.titleSmall

    fun applySchedule(startMs: Long, endMs: Long) {
        onScheduleChange(EventSchedule(startEpochMs = startMs, endEpochMs = endMs))
    }

    val dateRangeState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = localDateToUtcMidnightMillis(startLocal.date),
        initialSelectedEndDateMillis = localDateToUtcMidnightMillis(endLocal.date),
    )
    LaunchedEffect(uiState.showDatePicker, uiState.datePickerFocusEnd, schedule.startEpochMs, schedule.endEpochMs) {
        if (uiState.showDatePicker) {
            val startMs = localDateToUtcMidnightMillis(startLocal.date)
            if (uiState.datePickerFocusEnd) {
                // Keep start; clear end so the next calendar tap becomes the end date
                // and paints the in-range highlight immediately.
                dateRangeState.setSelection(startMs, null)
            } else {
                dateRangeState.setSelection(
                    startMs,
                    localDateToUtcMidnightMillis(endLocal.date),
                )
            }
        }
    }
    // Material DateRangePicker on some targets selects the end day without painting the
    // in-range span until selection is re-applied — re-set once both ends exist.
    LaunchedEffect(
        dateRangeState.selectedStartDateMillis,
        dateRangeState.selectedEndDateMillis,
        uiState.showDatePicker,
    ) {
        if (!uiState.showDatePicker) return@LaunchedEffect
        val startMs = dateRangeState.selectedStartDateMillis ?: return@LaunchedEffect
        val endMs = dateRangeState.selectedEndDateMillis ?: return@LaunchedEffect
        if (endMs >= startMs) {
            dateRangeState.setSelection(startMs, endMs)
        }
    }

    val dateRangeComplete =
        dateRangeState.selectedStartDateMillis != null &&
            dateRangeState.selectedEndDateMillis != null
    val dateRangeTooLong = run {
        val startMs = dateRangeState.selectedStartDateMillis ?: return@run false
        val endMs = dateRangeState.selectedEndDateMillis ?: return@run false
        endMs - startMs > MAX_EVENT_DURATION_MS
    }

    UnifiedPopupFormDialog(
        visible = uiState.showDatePicker,
        onDismissRequest = { uiState.showDatePicker = false },
        title = "Select date range",
        titleStyle = pickerDialogTitleStyle,
        contentMaxWidth = null,
        surfaceHorizontalPadding = PickerPopupSurfacePadding,
        innerPadding = PickerPopupInnerVerticalPadding,
        innerHorizontalPadding = PickerPopupChromePadding,
        bodyHorizontalPadding = 0.dp,
        motion = UnifiedPopupMotion.Picker,
        focusable = false,
        confirmLabel = "OK",
        confirmEnabled = dateRangeComplete && !dateRangeTooLong,
        onConfirm = {
            val startUtc = dateRangeState.selectedStartDateMillis ?: return@UnifiedPopupFormDialog
            val endUtc = dateRangeState.selectedEndDateMillis ?: return@UnifiedPopupFormDialog
            val startDate = utcMidnightMillisToLocalDate(startUtc)
            val endDate = utcMidnightMillisToLocalDate(endUtc)
            applySchedule(
                mergeLocalDateWithClock(startDate, startLocal.hour, startLocal.minute, tz),
                mergeLocalDateWithClock(endDate, endLocal.hour, endLocal.minute, tz),
            )
        },
        body = {
            if (uiState.showDatePicker) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val startUtc = dateRangeState.selectedStartDateMillis
                    val endUtc = dateRangeState.selectedEndDateMillis
                    if (startUtc != null) {
                        val startDate = utcMidnightMillisToLocalDate(startUtc)
                        val endDate = endUtc?.let { utcMidnightMillisToLocalDate(it) }
                        Text(
                            text = if (endDate != null) {
                                formatEventDateRangeLabel(
                                    mergeLocalDateWithClock(startDate, 0, 0, tz),
                                    mergeLocalDateWithClock(endDate, 0, 0, tz),
                                    tz,
                                )
                            } else {
                                "Select end date"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = GlassSheetTokens.OnOled(),
                            modifier = Modifier.padding(horizontal = PickerPopupChromePadding),
                        )
                        if (dateRangeTooLong) {
                            Text(
                                text = "Events can last at most 1 month.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(
                                    horizontal = PickerPopupChromePadding,
                                    vertical = 4.dp,
                                ),
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    DateRangePicker(
                        state = dateRangeState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(DateRangePickerHeight),
                        showModeToggle = false,
                        title = null,
                        headline = null,
                        colors = DatePickerDefaults.colors(
                            containerColor = GlassSheetTokens.OledBlack(),
                            titleContentColor = GlassSheetTokens.OnOled(),
                            headlineContentColor = GlassSheetTokens.OnOled(),
                            weekdayContentColor = GlassSheetTokens.OnOledMuted(),
                            subheadContentColor = GlassSheetTokens.OnOledMuted(),
                            navigationContentColor = GlassSheetTokens.OnOled(),
                            yearContentColor = GlassSheetTokens.OnOled(),
                            currentYearContentColor = GlassSheetTokens.OnOled(),
                            selectedYearContentColor = GlassSheetTokens.OnOled(),
                            selectedYearContainerColor = PrimaryBlue,
                            dayContentColor = GlassSheetTokens.OnOled(),
                            selectedDayContainerColor = PrimaryBlue,
                            selectedDayContentColor = GlassSheetTokens.OnOled(),
                            todayDateBorderColor = PrimaryBlue,
                            todayContentColor = PrimaryBlue,
                            dayInSelectionRangeContainerColor = PrimaryBlue.copy(alpha = 0.22f),
                            dayInSelectionRangeContentColor = GlassSheetTokens.OnOled(),
                        ),
                    )
                }
            }
        },
    )

    UnifiedPopupFormDialog(
        visible = uiState.showTimePicker,
        onDismissRequest = { uiState.showTimePicker = false },
        title = if (uiState.pickingStartTime) "Start time" else "End time",
        titleStyle = pickerDialogTitleStyle,
        contentMaxWidth = null,
        surfaceHorizontalPadding = PickerPopupSurfacePadding,
        innerPadding = PickerPopupInnerVerticalPadding,
        innerHorizontalPadding = PickerPopupChromePadding,
        bodyHorizontalPadding = PickerPopupChromePadding,
        motion = UnifiedPopupMotion.Picker,
        focusable = false,
        confirmLabel = "OK",
        onConfirm = {
            val (hour24, minute) = eventClock12hTo24h(
                EventClock12h(
                    hour12 = uiState.pendingHour12,
                    minute = uiState.pendingMinute,
                    isPm = uiState.pendingIsPm,
                ),
            )
            if (uiState.pickingStartTime) {
                val coerced = if (sameDay) {
                    coerceSameDayEventTimes(
                        editingStart = true,
                        startHour = hour24,
                        startMinute = minute,
                        endHour = endLocal.hour,
                        endMinute = endLocal.minute,
                    )
                } else {
                    (hour24 to minute) to (endLocal.hour to endLocal.minute)
                }
                applySchedule(
                    mergeLocalDateWithClock(startLocal.date, coerced.first.first, coerced.first.second, tz),
                    mergeLocalDateWithClock(endLocal.date, coerced.second.first, coerced.second.second, tz),
                )
            } else {
                val coerced = if (sameDay) {
                    coerceSameDayEventTimes(
                        editingStart = false,
                        startHour = startLocal.hour,
                        startMinute = startLocal.minute,
                        endHour = hour24,
                        endMinute = minute,
                    )
                } else {
                    (startLocal.hour to startLocal.minute) to (hour24 to minute)
                }
                applySchedule(
                    mergeLocalDateWithClock(startLocal.date, coerced.first.first, coerced.first.second, tz),
                    mergeLocalDateWithClock(endLocal.date, coerced.second.first, coerced.second.second, tz),
                )
            }
        },
        body = {
            if (uiState.showTimePicker) {
                EventTimeTumbler(
                    hour12 = uiState.pendingHour12,
                    minute = uiState.pendingMinute,
                    isPm = uiState.pendingIsPm,
                    onHour12Change = { uiState.pendingHour12 = it },
                    onMinuteChange = { uiState.pendingMinute = it },
                    onIsPmChange = { uiState.pendingIsPm = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun ScheduleActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(GlassSheetTokens.BentoInteriorCorner),
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryBlue,
            contentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EventTimeTumbler(
    hour12: Int,
    minute: Int,
    isPm: Boolean,
    onHour12Change: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onIsPmChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val capsuleShape = RoundedCornerShape(999.dp)
    val tumblerHeight = TumblerRowHeight * TumblerVisibleRows

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(tumblerHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TumblerRowHeight)
                .clip(capsuleShape)
                .background(GlassSheetTokens.GlassSurface())
                .border(1.dp, GlassSheetTokens.GlassBorder(), capsuleShape),
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TumblerColumn(
                values = (1..12).map { it.toString() },
                selectedIndex = (hour12 - 1).coerceIn(0, 11),
                onSelectedIndex = { onHour12Change(it + 1) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            TumblerColumn(
                values = (0..59).map { it.toString().padStart(2, '0') },
                selectedIndex = minute.coerceIn(0, 59),
                onSelectedIndex = onMinuteChange,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            TumblerColumn(
                values = listOf("AM", "PM"),
                selectedIndex = if (isPm) 1 else 0,
                onSelectedIndex = { onIsPmChange(it == 1) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun TumblerColumn(
    values: List<String>,
    selectedIndex: Int,
    onSelectedIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val edgePadding = TumblerRowHeight * ((TumblerVisibleRows - 1) / 2)
    val safeSelected = selectedIndex.coerceIn(0, (values.size - 1).coerceAtLeast(0))
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(safeSelected, values.size) {
        if (values.isEmpty()) return@LaunchedEffect
        if (listState.firstVisibleItemIndex != safeSelected || listState.firstVisibleItemScrollOffset != 0) {
            listState.animateScrollToItem(safeSelected)
        }
    }

    LaunchedEffect(listState, values.size) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling) {
                    val info = listState.layoutInfo
                    if (info.visibleItemsInfo.isEmpty()) return@collect
                    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
                    val closest = info.visibleItemsInfo.minByOrNull { item ->
                        abs((item.offset + item.size / 2) - viewportCenter)
                    } ?: return@collect
                    val index = closest.index.coerceIn(0, values.lastIndex)
                    if (index != safeSelected) {
                        onSelectedIndex(index)
                    }
                    listState.animateScrollToItem(index)
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = edgePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        flingBehavior = flingBehavior,
    ) {
        items(values.size, key = { it }) { index ->
            val selected = index == safeSelected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TumblerRowHeight)
                    .clickable {
                        onSelectedIndex(index)
                        scope.launch { listState.animateScrollToItem(index) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = values[index],
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        GlassSheetTokens.OnOled()
                    } else {
                        GlassSheetTokens.OnOledMuted()
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
