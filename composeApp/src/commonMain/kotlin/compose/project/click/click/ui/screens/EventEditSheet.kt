@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalLayoutApi::class)

package compose.project.click.click.ui.screens // pragma: allowlist secret

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.events.EVENT_CATEGORIES_MAX // pragma: allowlist secret
import compose.project.click.click.events.EVENT_CATEGORY_OPTIONS // pragma: allowlist secret
import compose.project.click.click.events.EVENT_CUSTOM_CATEGORY_MAX_LENGTH // pragma: allowlist secret
import compose.project.click.click.events.EVENT_DESCRIPTION_MAX_LENGTH // pragma: allowlist secret
import compose.project.click.click.events.EVENT_TITLE_MAX_LENGTH // pragma: allowlist secret
import compose.project.click.click.events.EventEditDraft // pragma: allowlist secret
import compose.project.click.click.events.EventVisibility // pragma: allowlist secret
import compose.project.click.click.events.GuestListVisibility // pragma: allowlist secret
import compose.project.click.click.events.customEventCategory // pragma: allowlist secret
import compose.project.click.click.events.eventEditValidationError // pragma: allowlist secret
import compose.project.click.click.events.eventSchedule // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatMediaPickers // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickChip // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickOutlinedTextField // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetChrome // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDefaults // pragma: allowlist secret
import compose.project.click.click.ui.components.EventDateTimePicker // pragma: allowlist secret
import compose.project.click.click.ui.components.EventSchedulePickerDialogs // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberEventSchedulePickerUiState // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.utils.GeocodedPlace // pragma: allowlist secret
import compose.project.click.click.utils.GeocodingService // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Preset chips plus up to [EVENT_CATEGORIES_MAX] total, with an "Add your own" field (F81). */
@Composable
internal fun EventCategoryPicker(
    selected: List<String>,
    onChange: (List<String>) -> Unit,
) {
    var custom by remember { mutableStateOf("") }
    val atMax = selected.size >= EVENT_CATEGORIES_MAX

    fun addCustom() {
        if (atMax) return
        val clean = customEventCategory(custom, selected) ?: return
        onChange(selected + clean)
        custom = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Categories", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val customChosen = selected.filterNot { chosen -> EVENT_CATEGORY_OPTIONS.any { it.equals(chosen, ignoreCase = true) } }
            (EVENT_CATEGORY_OPTIONS + customChosen).forEach { option ->
                val on = selected.any { it.equals(option, ignoreCase = true) }
                ClickChip(
                    label = option,
                    selected = on,
                    enabled = on || !atMax,
                    onClick = {
                        onChange(if (on) selected.filterNot { it.equals(option, ignoreCase = true) } else selected + option)
                    },
                    compact = true,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClickOutlinedTextField(
                value = custom,
                onValueChange = { custom = it.take(EVENT_CUSTOM_CATEGORY_MAX_LENGTH) },
                label = { Text("＋ Custom ($EVENT_CUSTOM_CATEGORY_MAX_LENGTH max)") },
                singleLine = true,
                enabled = !atMax,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = ::addCustom,
                enabled = !atMax && customEventCategory(custom, selected) != null,
            ) { Text("Add") }
        }
        Text(
            "Up to $EVENT_CATEGORIES_MAX.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Full creator edit for an event (F83), saved with one `PATCH /api/beacons/{id}`. */
@Composable
internal fun EventEditSheet(
    beacon: MapBeacon,
    saving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (draft: EventEditDraft, imageBytes: ByteArray?, imageMime: String?) -> Unit,
) {
    val initial = remember(beacon.id) { EventEditDraft.from(beacon) } ?: return
    val originalStart = remember(beacon.id) { beacon.eventSchedule()?.startEpochMs ?: 0L }
    var draft by remember(beacon.id) { mutableStateOf(initial) }
    var placeQuery by remember(beacon.id) {
        mutableStateOf(beacon.metadata.locationName ?: beacon.metadata.formattedAddress.orEmpty())
    }
    var placeResults by remember { mutableStateOf<List<GeocodedPlace>>(emptyList()) }
    var placeSearching by remember { mutableStateOf(false) }
    var placeJob by remember { mutableStateOf<Job?>(null) }
    var newPhoto by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scheduleUi = rememberEventSchedulePickerUiState()
    val pickers =
        rememberChatMediaPickers(
            onImagePicked = { bytes, mime ->
                newPhoto = bytes to mime
                draft = draft.copy(removePhoto = false)
            },
            onAudioPicked = { _, _, _ -> },
            onMediaAccessBlocked = { localError = it },
        )
    val hasPhoto = newPhoto != null || (!beacon.metadata.albumArtUrl.isNullOrBlank() && !draft.removePhoto)
    LaunchedEffect(draft) { localError = null }

    ClickFormBottomSheet(onDismissRequest = onDismiss) {
        ClickSheetChrome(
            title = "Edit event",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sheetBodyScroll()
                    .padding(horizontal = ClickSheetDefaults.ContentHorizontalPadding),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ClickOutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it.take(EVENT_TITLE_MAX_LENGTH)) },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ClickOutlinedTextField(
                    value = draft.description,
                    onValueChange = { draft = draft.copy(description = it.take(EVENT_DESCRIPTION_MAX_LENGTH)) },
                    label = { Text("Description") },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Place", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    ClickOutlinedTextField(
                        value = placeQuery,
                        onValueChange = { next ->
                            placeQuery = next.take(200)
                            placeJob?.cancel()
                            placeJob =
                                scope.launch {
                                    delay(220)
                                    val q = placeQuery.trim()
                                    if (q.length < 2) {
                                        placeResults = emptyList()
                                        return@launch
                                    }
                                    placeSearching = true
                                    val near = AppDataManager.lastKnownDeviceLocation.value
                                    placeResults =
                                        withContext(Dispatchers.Default) {
                                            GeocodingService.searchAddresses(
                                                query = q,
                                                limit = 5,
                                                nearLat = near?.first,
                                                nearLon = near?.second,
                                            )
                                        }
                                    placeSearching = false
                                }
                        },
                        label = { Text("Search address or place") },
                        singleLine = true,
                        trailingIcon = {
                            if (placeSearching) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    placeResults.forEach { place ->
                        TextButton(
                            onClick = {
                                draft = draft.copy(newPlace = place)
                                placeQuery = place.shortLabel
                                placeResults = emptyList()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(place.displayName, maxLines = 2, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (draft.newPlace != null) {
                        Text(
                            "The pin and check-in area move to the new place.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (newPhoto != null) {
                            "New photo selected"
                        } else if (hasPhoto) {
                            "Photo"
                        } else {
                            "No photo"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { pickers.openPhotoLibrary() }) { Text(if (hasPhoto) "Replace" else "Add photo") }
                    if (hasPhoto) {
                        TextButton(onClick = {
                            newPhoto = null
                            draft = draft.copy(removePhoto = true)
                        }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    }
                }

                EventDateTimePicker(
                    schedule = draft.schedule,
                    onScheduleChange = { draft = draft.copy(schedule = it) },
                    validationError = null,
                    uiState = scheduleUi,
                    includeDialogs = false,
                )

                EventCategoryPicker(selected = draft.categories, onChange = { draft = draft.copy(categories = it) })

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Event page", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EventVisibility.entries.forEach { option ->
                            ClickChip(
                                label =
                                    when (option) {
                                        EventVisibility.PUBLIC -> "Public"
                                        EventVisibility.UNLISTED -> "Unlisted"
                                        EventVisibility.INVITE_ONLY -> "Invite-only"
                                    },
                                selected = draft.listing.eventVisibility == option,
                                onClick = { draft = draft.copy(listing = draft.listing.copy(eventVisibility = option)) },
                                compact = true,
                            )
                        }
                    }
                    ClickOutlinedTextField(
                        value = draft.capacityText,
                        onValueChange = { text -> draft = draft.copy(capacityText = text.filter { it.isDigit() }.take(6)) },
                        label = { Text("Capacity (blank for unlimited)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Approval required", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Guests request to join; you approve RSVPs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = draft.listing.approvalRequired,
                            onCheckedChange = { draft = draft.copy(listing = draft.listing.copy(approvalRequired = it)) },
                        )
                    }
                    Text("Guest list on event page", style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GuestListVisibility.entries.forEach { option ->
                            ClickChip(
                                label = if (option == GuestListVisibility.PUBLIC) "Public" else "Hosts only",
                                selected = draft.listing.guestListVisibility == option,
                                onClick = { draft = draft.copy(listing = draft.listing.copy(guestListVisibility = option)) },
                                compact = true,
                            )
                        }
                    }
                }

                (localError ?: errorMessage)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
                    TextButton(
                        enabled = !saving,
                        onClick = {
                            val problem =
                                eventEditValidationError(
                                    draft,
                                    originalStart,
                                    compose.project.click.click.ui.screens
                                        .nowEpochMs(), // pragma: allowlist secret
                                )
                            if (problem != null) {
                                localError = problem
                            } else {
                                onSave(draft, newPhoto?.first, newPhoto?.second)
                            }
                        },
                    ) {
                        if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
                    }
                }
                Spacer(Modifier.height(ClickSheetDefaults.ContentBottomPadding))
            }
        }
    }
    EventSchedulePickerDialogs(
        schedule = draft.schedule,
        onScheduleChange = { draft = draft.copy(schedule = it) },
        uiState = scheduleUi,
    )
}
