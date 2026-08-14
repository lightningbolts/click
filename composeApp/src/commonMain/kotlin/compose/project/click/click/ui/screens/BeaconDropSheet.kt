@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.BeaconVisibilityAudience // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.events.EVENT_CATEGORY_OPTIONS // pragma: allowlist secret
import compose.project.click.click.events.EventSchedule // pragma: allowlist secret
import compose.project.click.click.events.EventScheduleValidationError // pragma: allowlist secret
import compose.project.click.click.events.EventVenueScale // pragma: allowlist secret
import compose.project.click.click.events.defaultEventSchedule // pragma: allowlist secret
import compose.project.click.click.events.validateEventSchedule // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatMediaPickers // pragma: allowlist secret
import compose.project.click.click.ui.components.ActionChipButton // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickFieldTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickOutlinedTextField // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDefaults // pragma: allowlist secret
import compose.project.click.click.ui.components.EventDateTimePicker // pragma: allowlist secret
import compose.project.click.click.ui.components.EventSchedulePickerDialogs // pragma: allowlist secret
import compose.project.click.click.ui.components.LocalSheetOnDismissRequest // pragma: allowlist secret
import compose.project.click.click.ui.components.MediaSourceButton // pragma: allowlist secret
import compose.project.click.click.ui.components.ProvideSheetSwipeDismiss // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberEventSchedulePickerUiState // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberSheetScrollAtTop // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetImePadding // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import compose.project.click.click.utils.GeocodedPlace // pragma: allowlist secret
import compose.project.click.click.utils.GeocodingService // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Beacon drop types exposed in the map FAB flow.
 */
enum class BeaconDropCategory {
    SOUNDTRACK,
    HAZARD,
    UTILITY,
    SOS,
    STUDY,
    EVENT,
    COMMUNITY_HUB,
}

/**
 * The complete set of field requirements for dropping a beacon, or `null` when the form can submit.
 *
 * Pure so the requirements are testable and visible in one place. Notably **a photo is not one of
 * them** for any category: photos are encouraged in the sheet, and a beacon without one falls back
 * to its generated gradient. Event schedule validation stays in the sheet because it renders inline
 * on the date pickers rather than as a submit error.
 */
fun beaconDropValidationError(
    category: BeaconDropCategory,
    title: String,
    soundtrackUrl: String?,
    hasEventLocation: Boolean,
): String? {
    val isSoundtrack = category == BeaconDropCategory.SOUNDTRACK
    if (!isSoundtrack && title.isBlank()) return "Please add a title."
    if (isSoundtrack && soundtrackUrl.isNullOrBlank()) return "Please add a music link."
    if (category == BeaconDropCategory.EVENT && !hasEventLocation) {
        return "Set an event location (search an address or use my location)."
    }
    return null
}

/**
 * Beacon time-to-live presets. Independent of availability-intent durations so beacons can live up
 * to 7 days (backend caps at 30 days) while keeping the full short-duration granularity. The label
 * maps directly to the chip text.
 */
enum class BeaconDuration(
    val durationMs: Long,
    val label: String,
) {
    FIFTEEN_MIN(15L * 60_000L, "15 min"),
    THIRTY_MIN(30L * 60_000L, "30 min"),
    FORTY_FIVE_MIN(45L * 60_000L, "45 min"),
    ONE_HOUR(60L * 60_000L, "1 hour"),
    NINETY_MIN(90L * 60_000L, "90 min"),
    TWO_HOURS(2L * 60L * 60_000L, "2 hours"),
    THREE_HOURS(3L * 60L * 60_000L, "3 hours"),
    SIX_HOURS(6L * 60L * 60_000L, "6 hours"),
    TWENTY_FOUR_HOURS(24L * 60L * 60_000L, "24 hours"),
    TWO_DAYS(2L * 24L * 60L * 60_000L, "2 days"),
    THREE_DAYS(3L * 24L * 60L * 60_000L, "3 days"),
    FOUR_DAYS(4L * 24L * 60L * 60_000L, "4 days"),
    FIVE_DAYS(5L * 24L * 60L * 60_000L, "5 days"),
    SIX_DAYS(6L * 24L * 60L * 60_000L, "6 days"),
    SEVEN_DAYS(7L * 24L * 60L * 60_000L, "7 days"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BeaconDropSheetContent(
    errorMessage: String?,
    onDismissError: () -> Unit,
    onSubmit: (
        kind: MapBeaconKind,
        title: String,
        description: String?,
        soundtrackUrl: String?,
        ttlMs: Long?,
        showCreatorName: Boolean,
        visibilityAudience: BeaconVisibilityAudience,
        eventSchedule: compose.project.click.click.events.EventSchedule?, // pragma: allowlist secret
        eventCategories: List<String>,
        venueScale: compose.project.click.click.events.EventVenueScale, // pragma: allowlist secret
        eventLocation: GeocodedPlace?,
        imageBytes: ByteArray?,
        imageMime: String?,
        onRejectedEarly: () -> Unit,
    ) -> Unit,
    onCreateHub: () -> Unit = {},
    onResolveCurrentLocation: suspend () -> GeocodedPlace? = { null },
    submitLocked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val dismissKeyboard: () -> Unit =
        remember(focusManager) {
            { focusManager.clearFocus() }
        }
    var isSubmitting by remember { mutableStateOf(false) }
    val category = remember { mutableStateOf(BeaconDropCategory.SOUNDTRACK) }
    var beaconTitleDraft by remember { mutableStateOf("") }
    var beaconDescriptionDraft by remember { mutableStateOf("") }
    var soundtrackUrlDraft by remember { mutableStateOf("") }
    val expiration = remember { mutableStateOf(BeaconDuration.THREE_HOURS) }
    var eventSchedule by remember { mutableStateOf(defaultEventSchedule()) }
    var eventScheduleError by remember { mutableStateOf<EventScheduleValidationError?>(null) }
    var eventCategories by remember { mutableStateOf(setOf<String>()) }
    var venueScale by remember { mutableStateOf(EventVenueScale.DEFAULT) }
    var submitValidationError by remember { mutableStateOf<String?>(null) }
    var addressQuery by remember { mutableStateOf("") }
    var addressSuggestions by remember { mutableStateOf<List<GeocodedPlace>>(emptyList()) }
    var selectedEventLocation by remember { mutableStateOf<GeocodedPlace?>(null) }
    var addressSearching by remember { mutableStateOf(false) }
    var resolvingCurrentLocation by remember { mutableStateOf(false) }
    var addressSearchJob by remember { mutableStateOf<Job?>(null) }

    var showCreatorName by remember { mutableStateOf(false) }
    var visibilityAudience by remember { mutableStateOf(BeaconVisibilityAudience.EVERYONE) }
    var beaconImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var beaconImageMime by remember { mutableStateOf<String?>(null) }
    val mediaPickers =
        rememberChatMediaPickers(
            onImagePicked = { bytes, mime ->
                beaconImageBytes = bytes
                beaconImageMime = mime
                submitValidationError = null
            },
            onAudioPicked = { _, _, _ -> },
        )

    val isSoundtrack = category.value == BeaconDropCategory.SOUNDTRACK
    val isEvent = category.value == BeaconDropCategory.EVENT

    val kind =
        when (category.value) {
            BeaconDropCategory.SOUNDTRACK -> MapBeaconKind.SOUNDTRACK
            BeaconDropCategory.HAZARD -> MapBeaconKind.HAZARD
            BeaconDropCategory.UTILITY -> MapBeaconKind.UTILITY
            BeaconDropCategory.SOS -> MapBeaconKind.SOS
            BeaconDropCategory.STUDY -> MapBeaconKind.STUDY
            BeaconDropCategory.EVENT -> MapBeaconKind.EVENT
            BeaconDropCategory.COMMUNITY_HUB -> MapBeaconKind.OTHER
        }
    val chipContainer = MaterialTheme.colorScheme.surfaceContainerHighest
    val chipSelected = MaterialTheme.colorScheme.primaryContainer
    val scroll = rememberScrollState()
    val scrollAtTop = rememberSheetScrollAtTop(scroll)
    val schedulePickerUi = rememberEventSchedulePickerUiState()
    val fieldColors =
        OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )

    val onSheetDismiss = LocalSheetOnDismissRequest.current
    // Report scroll edge to ClickPlatformSheet's dismiss host — do not attach a second
    // nested-scroll dismiss (double surface-drag was opening map gaps above the sheet).
    ProvideSheetSwipeDismiss(
        onDismissRequest = onSheetDismiss,
        scrollAtTop = scrollAtTop,
    ) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .sheetImePadding(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .verticalScroll(scroll)
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = ClickSheetDefaults.ContentTopPaddingUnderGrabber,
                            bottom = 12.dp,
                        ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Drop a community beacon",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = BeaconDropCategory.entries.toList(),
                        key = { it.name },
                    ) { cat ->
                        FilterChip(
                            selected = category.value == cat,
                            onClick = {
                                if (cat == BeaconDropCategory.COMMUNITY_HUB) {
                                    focusManager.clearFocus(force = true)
                                    onCreateHub()
                                    return@FilterChip
                                }
                                if (category.value == cat) return@FilterChip
                                // Dismiss IME before swapping Event forms to avoid sheet height thrash.
                                focusManager.clearFocus(force = true)
                                scope.launch {
                                    delay(40)
                                    category.value = cat
                                    submitValidationError = null
                                    onDismissError()
                                }
                            },
                            label = {
                                Text(
                                    when (cat) {
                                        BeaconDropCategory.SOUNDTRACK -> "Soundtrack"
                                        BeaconDropCategory.HAZARD -> "Hazard"
                                        BeaconDropCategory.UTILITY -> "Utility"
                                        BeaconDropCategory.SOS -> "SOS"
                                        BeaconDropCategory.STUDY -> "Study"
                                        BeaconDropCategory.EVENT -> "Event"
                                        BeaconDropCategory.COMMUNITY_HUB -> "Hub"
                                    },
                                )
                            },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    containerColor = chipContainer,
                                    selectedContainerColor = chipSelected,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                ),
                        )
                    }
                }

                if (isSoundtrack) {
                    BeaconDropOutlinedField(
                        value = soundtrackUrlDraft,
                        onValueChange = {
                            if (it.length <= 2000) {
                                soundtrackUrlDraft = it
                                onDismissError()
                            }
                        },
                        placeholder = "Spotify, Apple Music, or YouTube link",
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    clipboardManager.getText()?.text?.let { pasted ->
                                        soundtrackUrlDraft = pasted.trim()
                                        onDismissError()
                                    }
                                },
                                // 36dp instead of 40dp so a long hint has room before ellipsizing.
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentPaste,
                                    contentDescription = "Paste link",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        colors = fieldColors,
                        onDismissKeyboard = dismissKeyboard,
                        keyboardType = KeyboardType.Uri,
                    )
                } else {
                    BeaconDropOutlinedField(
                        value = beaconTitleDraft,
                        onValueChange = {
                            if (it.length <= 80) {
                                beaconTitleDraft = it
                                onDismissError()
                            }
                        },
                        placeholder =
                            when (category.value) {
                                BeaconDropCategory.HAZARD -> "What’s the hazard?"
                                BeaconDropCategory.SOS -> "What do you need help with?"
                                BeaconDropCategory.UTILITY -> "What did you find?"
                                BeaconDropCategory.STUDY -> "Study spot name"
                                BeaconDropCategory.EVENT -> "Event title (max 80)"
                                else -> "Title (max 80)"
                            },
                        singleLine = true,
                        trailingIcon = null,
                        colors = fieldColors,
                        onDismissKeyboard = dismissKeyboard,
                    )
                    if (isEvent) {
                        EventDateTimePicker(
                            schedule = eventSchedule,
                            onScheduleChange = { next ->
                                eventSchedule = next
                                eventScheduleError = validateEventSchedule(next.startEpochMs, next.endEpochMs)
                                onDismissError()
                            },
                            validationError = eventScheduleError,
                            uiState = schedulePickerUi,
                            includeDialogs = false,
                        )
                        Text(
                            text = "Categories",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EVENT_CATEGORY_OPTIONS.forEach { option ->
                                FilterChip(
                                    selected = option in eventCategories,
                                    onClick = {
                                        eventCategories =
                                            if (option in eventCategories) {
                                                eventCategories - option
                                            } else {
                                                eventCategories + option
                                            }
                                    },
                                    label = { Text(option) },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            containerColor = chipContainer,
                                            selectedContainerColor = chipSelected,
                                            labelColor = MaterialTheme.colorScheme.onSurface,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                )
                            }
                        }
                        Text(
                            text = "Check-in area",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "How big is the place? Set the address near the center of the gathering.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EventVenueScale.entries.forEach { option ->
                                FilterChip(
                                    selected = venueScale == option,
                                    onClick = { venueScale = option },
                                    label = { Text(option.label) },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            containerColor = chipContainer,
                                            selectedContainerColor = chipSelected,
                                            labelColor = MaterialTheme.colorScheme.onSurface,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                )
                            }
                        }
                        Text(
                            text = "Event location",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Search an address so you can create the event without being there. Guests still check in at the venue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BeaconDropOutlinedField(
                            value = addressQuery,
                            onValueChange = { next ->
                                addressQuery = next.take(200)
                                selectedEventLocation = null
                                submitValidationError = null
                                onDismissError()
                                addressSearchJob?.cancel()
                                addressSearchJob =
                                    scope.launch {
                                        delay(220)
                                        val q = addressQuery.trim()
                                        if (q.length < 2) {
                                            addressSuggestions = emptyList()
                                            addressSearching = false
                                            return@launch
                                        }
                                        addressSearching = true
                                        val near = AppDataManager.lastKnownDeviceLocation.value
                                        val results =
                                            withContext(Dispatchers.Default) {
                                                GeocodingService.searchAddresses(
                                                    query = q,
                                                    limit = 5,
                                                    nearLat = near?.first,
                                                    nearLon = near?.second,
                                                )
                                            }
                                        if (addressQuery.trim() == q) {
                                            addressSuggestions = results
                                        }
                                        addressSearching = false
                                    }
                            },
                            placeholder = "Search address or place",
                            singleLine = true,
                            trailingIcon = {
                                if (addressSearching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Place,
                                        contentDescription = null,
                                    )
                                }
                            },
                            colors = fieldColors,
                            onDismissKeyboard = dismissKeyboard,
                        )
                        TextButton(
                            onClick = {
                                if (resolvingCurrentLocation) return@TextButton
                                scope.launch {
                                    resolvingCurrentLocation = true
                                    submitValidationError = null
                                    onDismissError()
                                    val place =
                                        withContext(Dispatchers.Default) {
                                            onResolveCurrentLocation()
                                        }
                                    if (place != null) {
                                        selectedEventLocation = place
                                        addressQuery = place.shortLabel
                                        addressSuggestions = emptyList()
                                    } else {
                                        submitValidationError =
                                            "Could not read your location. Enable GPS or search an address."
                                    }
                                    resolvingCurrentLocation = false
                                }
                            },
                            enabled = !resolvingCurrentLocation && !submitLocked,
                        ) {
                            if (resolvingCurrentLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.MyLocation,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                            }
                            Text(if (resolvingCurrentLocation) "Getting location…" else "Use my location")
                        }
                        selectedEventLocation?.let { place ->
                            Text(
                                text = place.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (addressSuggestions.isNotEmpty() && selectedEventLocation == null) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                addressSuggestions.forEach { suggestion ->
                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedEventLocation = suggestion
                                                    addressQuery = suggestion.shortLabel
                                                    addressSuggestions = emptyList()
                                                    dismissKeyboard()
                                                    onDismissError()
                                                }.padding(vertical = 8.dp),
                                    ) {
                                        Text(
                                            text = suggestion.shortLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        if (suggestion.displayName != suggestion.shortLabel) {
                                            Text(
                                                text = suggestion.displayName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text =
                                when (category.value) {
                                    BeaconDropCategory.HAZARD -> "Hazard details"
                                    BeaconDropCategory.SOS -> "SOS details"
                                    BeaconDropCategory.UTILITY -> "Utility details"
                                    BeaconDropCategory.STUDY -> "Study spot details"
                                    else -> "Beacon details"
                                },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text =
                                when (category.value) {
                                    BeaconDropCategory.HAZARD ->
                                        "Warn people nearby about something to avoid. Pin drops at your current location."
                                    BeaconDropCategory.SOS ->
                                        "Ask for help nearby. Pin drops at your current location."
                                    BeaconDropCategory.UTILITY ->
                                        "Share a useful find nearby (outlet, water, restroom). Pin drops at your current location."
                                    BeaconDropCategory.STUDY ->
                                        "Mark a quiet place to work. Pin drops at your current location."
                                    else ->
                                        "Pin drops at your current location."
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Visible for",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "How long should this pin stay on the map?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            BeaconDuration.entries.forEach { opt ->
                                FilterChip(
                                    selected = expiration.value == opt,
                                    onClick = {
                                        expiration.value = opt
                                        onDismissError()
                                    },
                                    label = { Text(opt.label) },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            containerColor = chipContainer,
                                            selectedContainerColor = chipSelected,
                                            labelColor = MaterialTheme.colorScheme.onSurface,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                )
                            }
                        }
                    }
                    BeaconDropOutlinedField(
                        value = beaconDescriptionDraft,
                        onValueChange = {
                            if (it.length <= 500) {
                                beaconDescriptionDraft = it
                                onDismissError()
                            }
                        },
                        placeholder = "Description (optional, max 500)",
                        singleLine = false,
                        trailingIcon = null,
                        colors = fieldColors,
                        onDismissKeyboard = dismissKeyboard,
                    )
                }

                // Optional for every category, including soundtracks — a beacon without a photo
                // falls back to its generated gradient wherever it is rendered.
                Text(
                    text = "Photo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        if (beaconImageBytes != null) {
                            "Photo attached"
                        } else {
                            "Add a photo so people recognize this at a glance."
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (beaconImageBytes != null) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MediaSourceButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.PhotoCamera,
                        label = "Take photo",
                        onClick = { mediaPickers.openCamera() },
                    )
                    MediaSourceButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Image,
                        label = "Photo library",
                        onClick = { mediaPickers.openPhotoLibrary() },
                    )
                }
                beaconImageBytes?.let { bytes ->
                    BeaconAttachedPhotoThumbnail(
                        bytes = bytes,
                        onReplace = { mediaPickers.openCamera() },
                        onRemove = {
                            beaconImageBytes = null
                            beaconImageMime = null
                        },
                    )
                }

                Text(
                    text = "Who can see this",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = BeaconVisibilityAudience.entries.toList(),
                        key = { it.name },
                    ) { option ->
                        FilterChip(
                            selected = visibilityAudience == option,
                            onClick = { visibilityAudience = option },
                            label = {
                                Text(
                                    when (option) {
                                        BeaconVisibilityAudience.EVERYONE -> "Everyone"
                                        BeaconVisibilityAudience.CONNECTIONS -> "Connections only"
                                        BeaconVisibilityAudience.CORE_CONNECTIONS -> "Core connections only"
                                    },
                                )
                            },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    containerColor = chipContainer,
                                    selectedContainerColor = chipSelected,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                ),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Display my name",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Show your name on the map pin for others nearby.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = showCreatorName,
                        onCheckedChange = { showCreatorName = it },
                    )
                }

                listOfNotNull(
                    submitValidationError?.takeIf { it.isNotBlank() },
                    errorMessage?.takeIf { it.isNotBlank() },
                ).firstOrNull()?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = {
                        if (isSubmitting) return@Button
                        dismissKeyboard()
                        submitValidationError = null
                        isSubmitting = true
                        val ttl =
                            if (isSoundtrack || isEvent) {
                                null
                            } else {
                                expiration.value.durationMs
                            }
                        val title = beaconTitleDraft.trim()
                        val description = beaconDescriptionDraft.trim().ifBlank { null }
                        val url = if (isSoundtrack) soundtrackUrlDraft.trim().ifBlank { null } else null
                        val fieldError =
                            beaconDropValidationError(
                                category = category.value,
                                title = title,
                                soundtrackUrl = url,
                                hasEventLocation = selectedEventLocation != null,
                            )
                        if (fieldError != null) {
                            submitValidationError = fieldError
                            isSubmitting = false
                            return@Button
                        }
                        val schedule =
                            if (isEvent) {
                                eventScheduleError =
                                    validateEventSchedule(
                                        eventSchedule.startEpochMs,
                                        eventSchedule.endEpochMs,
                                    )
                                if (eventScheduleError != null) {
                                    isSubmitting = false
                                    return@Button
                                }
                                eventSchedule
                            } else {
                                null
                            }
                        onSubmit(
                            kind,
                            title,
                            description,
                            url,
                            ttl,
                            showCreatorName,
                            visibilityAudience,
                            schedule,
                            if (isEvent) eventCategories.toList() else emptyList(),
                            if (isEvent) venueScale else EventVenueScale.DEFAULT,
                            if (isEvent) selectedEventLocation else null,
                            beaconImageBytes,
                            beaconImageMime,
                        ) {
                            isSubmitting = false
                        }
                    },
                    enabled = !isSubmitting && !submitLocked,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text("Drop pin")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (isEvent) {
                EventSchedulePickerDialogs(
                    schedule = eventSchedule,
                    onScheduleChange = { next ->
                        eventSchedule = next
                        eventScheduleError = validateEventSchedule(next.startEpochMs, next.endEpochMs)
                        onDismissError()
                    },
                    uiState = schedulePickerUi,
                )
            }
        }
    }
}

/**
 * Confirms what will be uploaded and makes the optional state obvious: the photo can be swapped or
 * dropped entirely without cancelling the drop.
 */
@Composable
private fun BeaconAttachedPhotoThumbnail(
    bytes: ByteArray,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = bytes,
            contentDescription = "Attached beacon photo",
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(clickBorderWidth(), clickBorderColor(), RoundedCornerShape(12.dp)),
        )
        ActionChipButton(
            label = "Replace",
            onClick = onReplace,
        )
        ActionChipButton(
            label = "Remove",
            onClick = onRemove,
            leadingIcon = Icons.Filled.Close,
            contentDescription = null,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BeaconDropOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    trailingIcon: @Composable (() -> Unit)?,
    colors: androidx.compose.material3.TextFieldColors,
    onDismissKeyboard: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val lineCount = if (singleLine) 1 else 3
    ClickOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholderText = placeholder,
        singleLine = singleLine,
        minLines = lineCount,
        maxLines = if (singleLine) 1 else 6,
        minHeight = if (singleLine) ClickFieldTokens.SingleLineMinHeight else ClickFieldTokens.MultilineMinHeight,
        keyboardOptions =
            if (singleLine) {
                KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done,
                )
            } else {
                KeyboardOptions(imeAction = ImeAction.Done)
            },
        keyboardActions = KeyboardActions(onDone = { onDismissKeyboard() }),
        trailingIcon = trailingIcon,
        colors = colors,
        shape = ClickFieldTokens.Shape,
    )
}
