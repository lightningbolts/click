@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.FriendshipSpot // pragma: allowlist secret
import compose.project.click.click.data.models.HangoutPlan // pragma: allowlist secret
import compose.project.click.click.data.models.PlanDraftRules // pragma: allowlist secret
import compose.project.click.click.data.models.PlanIdeasStore // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickDateTimePickerDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetChrome // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDefaults // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetPrimaryButton // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.util.formatClockTime // pragma: allowlist secret
import compose.project.click.click.util.formatRelativeDay // pragma: allowlist secret
import compose.project.click.click.utils.GeocodedPlace // pragma: allowlist secret
import compose.project.click.click.utils.GeocodingService // pragma: allowlist secret
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

private data class PlanPlace(
    val name: String,
    val latitude: Double?,
    val longitude: Double?,
)

private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

private const val YEAR_MS: Long = 365L * 24 * 60 * 60 * 1000

/**
 * Plan a hangout (iOS `PlanHangoutSheet`): what (title + idea chips), when (day chips, start, optional
 * end), and an optional place (search, or places you've met in a direct chat). Everyone in the chat
 * is invited.
 */
@Composable
internal fun PlanHangoutSheet(
    chatName: String,
    /** Named spots you've met this person at (direct chats); empty for groups. */
    metSpots: List<FriendshipSpot>,
    nearLatitude: Double?,
    nearLongitude: Double?,
    onDismiss: () -> Unit,
    onSend: (HangoutPlan) -> Unit,
) {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val openedAt = remember { nowMs() }
    val storage = remember { createTokenStorage() }
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var customIdeas by remember { mutableStateOf<List<String>>(emptyList()) }
    var start by remember { mutableLongStateOf(PlanDraftRules.defaultStart(openedAt, timeZone)) }
    var end by remember { mutableStateOf<Long?>(null) }
    var place by remember { mutableStateOf<PlanPlace?>(null) }
    var placeQuery by remember { mutableStateOf("") }
    var placeResults by remember { mutableStateOf<List<GeocodedPlace>>(emptyList()) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var addingIdea by remember { mutableStateOf(false) }
    var newIdea by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { customIdeas = PlanIdeasStore.load(storage) }
    LaunchedEffect(placeQuery) {
        val q = placeQuery.trim()
        if (q.length < 2 || q == place?.name) {
            placeResults = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        placeResults =
            runCatching {
                GeocodingService.searchAddresses(
                    q,
                    limit = 5,
                    nearLat = nearLatitude,
                    nearLon = nearLongitude,
                )
            }.getOrDefault(emptyList())
    }

    fun setStart(newStart: Long) {
        end = PlanDraftRules.shiftEnd(start, newStart, end)
        start = newStart
    }

    ClickFormBottomSheet(onDismissRequest = onDismiss, fillBody = true) {
        ClickSheetChrome(
            title = "Plan with $chatName",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sheetBodyScroll()
                    .padding(horizontal = ClickSheetDefaults.ContentHorizontalPadding),
        ) {
            SectionLabel("What")
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(HangoutPlan.TITLE_MAX) },
                placeholder = { Text("Dinner, a walk, anything") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (customIdeas + PlanDraftRules.defaultIdeas).forEach { idea ->
                    val isCustom = idea in customIdeas
                    FilterChip(
                        selected = title == idea.dropEmojiPrefix(),
                        onClick = { title = idea.dropEmojiPrefix() },
                        label = { Text(idea) },
                        modifier =
                            if (isCustom) {
                                Modifier.combinedClickable(onClick = { title = idea }, onLongClick = {
                                    customIdeas = customIdeas - idea
                                    scope.launch { PlanIdeasStore.save(storage, customIdeas) }
                                })
                            } else {
                                Modifier
                            },
                    )
                }
                FilterChip(selected = false, onClick = { addingIdea = true }, label = { Text("＋ Add your own") })
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("When")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlanDraftRules.dayChips(openedAt, timeZone).forEach { day ->
                    val dayMs = day.atStartOfDayIn(timeZone).toEpochMilliseconds()
                    val selected = formatRelativeDay(start, openedAt, timeZone) == formatRelativeDay(dayMs, openedAt, timeZone)
                    FilterChip(
                        selected = selected,
                        onClick = { setStart(PlanDraftRules.moveToDay(start, day, timeZone)) },
                        label = { Text(formatRelativeDay(dayMs, openedAt, timeZone).dayChipLabel(day)) },
                    )
                }
            }
            PickerRow(label = "Starts", value = "${formatRelativeDay(start, nowMs(), timeZone)} · ${formatClockTime(start, timeZone)}") {
                pickingStart = true
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("End time", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = end != null,
                    onCheckedChange = { on -> end = if (on) start + PlanDraftRules.DEFAULT_LENGTH_MS else null },
                )
            }
            end?.let { e ->
                PickerRow(label = "Ends", value = "${formatRelativeDay(e, nowMs(), timeZone)} · ${formatClockTime(e, timeZone)}") {
                    pickingEnd = true
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("Where (optional)")
            if (metSpots.isNotEmpty()) {
                Text("Places you've met", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    metSpots.filter { !it.name.isNullOrBlank() }.take(8).forEach { spot ->
                        FilterChip(
                            selected = place?.name == spot.name,
                            onClick = {
                                place = PlanPlace(spot.name!!, spot.latitude, spot.longitude)
                                placeQuery = spot.name
                            },
                            label = { Text(spot.name!!, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = placeQuery,
                onValueChange = {
                    placeQuery = it
                    if (it.isBlank()) {
                        place = null
                    } else if (place?.name != it) {
                        place = PlanPlace(it.trim(), null, null)
                    }
                },
                placeholder = { Text("Search a place") },
                leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            placeResults.forEach { result ->
                Text(
                    result.shortLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = {
                                place = PlanPlace(result.shortLabel, result.latitude, result.longitude)
                                placeQuery = result.shortLabel
                                placeResults = emptyList()
                            })
                            .padding(vertical = 10.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            ClickSheetPrimaryButton(
                text = "Send plan",
                enabled = PlanDraftRules.canSend(title, start, end, nowMs()),
                onClick = {
                    val p = place
                    onSend(
                        HangoutPlan(
                            title = title.trim(),
                            startsAtEpochMs = start,
                            endsAtEpochMs = end,
                            placeName = p?.name?.takeIf { it.isNotBlank() },
                            latitude = p?.latitude,
                            longitude = p?.longitude,
                        ),
                    )
                },
            )
            Spacer(Modifier.height(ClickSheetDefaults.ContentBottomPadding))
        }
    }

    if (pickingStart) {
        val now = nowMs()
        ClickDateTimePickerDialog(
            initialEpochMs = start,
            minEpochMs = now + 60_000L,
            maxEpochMs = now + YEAR_MS,
            onPicked = {
                setStart(it)
                pickingStart = false
            },
            onDismiss = { pickingStart = false },
        )
    }
    val currentEnd = end
    if (pickingEnd && currentEnd != null) {
        ClickDateTimePickerDialog(
            initialEpochMs = currentEnd,
            minEpochMs = start + PlanDraftRules.MIN_LENGTH_MS,
            maxEpochMs = start + YEAR_MS,
            onPicked = {
                end = PlanDraftRules.clampEnd(start, it)
                pickingEnd = false
            },
            onDismiss = { pickingEnd = false },
        )
    }
    if (addingIdea) {
        AlertDialog(
            onDismissRequest = { addingIdea = false },
            title = { Text("Add an idea") },
            text = {
                OutlinedTextField(
                    value = newIdea,
                    onValueChange = { newIdea = it.take(PlanDraftRules.CUSTOM_IDEA_MAX) },
                    singleLine = true,
                    placeholder = { Text("🎳 Bowling") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    PlanIdeasStore.normalize(newIdea)?.let { idea ->
                        customIdeas = (listOf(idea) + customIdeas).distinct()
                        scope.launch { PlanIdeasStore.save(storage, customIdeas) }
                        title = idea.dropEmojiPrefix()
                    }
                    newIdea = ""
                    addingIdea = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addingIdea = false }) { Text("Cancel") } },
        )
    }
}

/** "☕️ Coffee" -> "Coffee": the idea chip fills the title without its emoji. */
private fun String.dropEmojiPrefix(): String {
    val parts = trim().split(' ', limit = 2)
    return if (parts.size == 2 && parts[0].none { it.isLetterOrDigit() }) parts[1].trim() else trim()
}

/** Weekday chips beyond tomorrow use a short name ("Sat") so the row stays compact. */
private fun String.dayChipLabel(day: LocalDate): String =
    if (this == "Today" || this == "Tomorrow") {
        this
    } else {
        day.dayOfWeek.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
            .take(3)
    }

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun PickerRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onClick) { Text("Change") }
        Spacer(Modifier.size(0.dp))
    }
}
