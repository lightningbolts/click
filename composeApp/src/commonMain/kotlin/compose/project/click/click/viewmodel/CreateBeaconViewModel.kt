package compose.project.click.click.viewmodel // pragma: allowlist secret

import androidx.lifecycle.ViewModel
import compose.project.click.click.data.models.BeaconVisibilityAudience // pragma: allowlist secret
import compose.project.click.click.events.DEFAULT_EVENT_OCCURRENCES // pragma: allowlist secret
import compose.project.click.click.events.EVENT_OCCURRENCE_RANGE // pragma: allowlist secret
import compose.project.click.click.events.EventRecurrence // pragma: allowlist secret
import compose.project.click.click.events.EventRecurrenceFrequency // pragma: allowlist secret
import compose.project.click.click.events.EventSchedule // pragma: allowlist secret
import compose.project.click.click.events.EventScheduleValidationError // pragma: allowlist secret
import compose.project.click.click.events.EventVenueScale // pragma: allowlist secret
import compose.project.click.click.events.EventVisibility // pragma: allowlist secret
import compose.project.click.click.events.GuestListVisibility // pragma: allowlist secret
import compose.project.click.click.events.defaultEventSchedule // pragma: allowlist secret
import compose.project.click.click.ui.screens.BeaconDropCategory // pragma: allowlist secret
import compose.project.click.click.ui.screens.BeaconDuration // pragma: allowlist secret
import compose.project.click.click.utils.GeocodedPlace // pragma: allowlist secret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Survives camera / picker activity recreation so category, drafts, and the staged photo do not
 * reset to Soundtrack / empty when the native capture UI dismisses.
 */
data class CreateBeaconUiState(
    val category: BeaconDropCategory = BeaconDropCategory.SOUNDTRACK,
    val title: String = "",
    val description: String = "",
    val soundtrackUrl: String = "",
    val expiration: BeaconDuration = BeaconDuration.THREE_HOURS,
    val eventSchedule: EventSchedule = defaultEventSchedule(),
    val eventScheduleError: EventScheduleValidationError? = null,
    val eventCategories: Set<String> = emptySet(),
    /** Null = a one-off event. */
    val repeatFrequency: EventRecurrenceFrequency? = null,
    val occurrences: Int = DEFAULT_EVENT_OCCURRENCES,
    val venueScale: EventVenueScale = EventVenueScale.DEFAULT,
    val submitValidationError: String? = null,
    val addressQuery: String = "",
    val addressSuggestions: List<GeocodedPlace> = emptyList(),
    val selectedEventLocation: GeocodedPlace? = null,
    val addressSearching: Boolean = false,
    val resolvingCurrentLocation: Boolean = false,
    val showCreatorName: Boolean = false,
    val visibilityAudience: BeaconVisibilityAudience = BeaconVisibilityAudience.EVERYONE,
    val eventVisibility: EventVisibility = EventVisibility.PUBLIC,
    val eventCapacityText: String = "",
    val approvalRequired: Boolean = false,
    val guestListVisibility: GuestListVisibility = GuestListVisibility.PUBLIC,
    val stagedPhotoBytes: ByteArray? = null,
    val stagedPhotoMime: String? = null,
    val isSubmitting: Boolean = false,
) {
    val hasStagedPhoto: Boolean get() = stagedPhotoBytes != null

    val eventRecurrence: EventRecurrence?
        get() = repeatFrequency?.let { EventRecurrence(frequency = it.apiValue, count = occurrences) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CreateBeaconUiState) return false
        return category == other.category &&
            title == other.title &&
            description == other.description &&
            soundtrackUrl == other.soundtrackUrl &&
            expiration == other.expiration &&
            eventSchedule == other.eventSchedule &&
            eventScheduleError == other.eventScheduleError &&
            eventCategories == other.eventCategories &&
            repeatFrequency == other.repeatFrequency &&
            occurrences == other.occurrences &&
            venueScale == other.venueScale &&
            submitValidationError == other.submitValidationError &&
            addressQuery == other.addressQuery &&
            addressSuggestions == other.addressSuggestions &&
            selectedEventLocation == other.selectedEventLocation &&
            addressSearching == other.addressSearching &&
            resolvingCurrentLocation == other.resolvingCurrentLocation &&
            showCreatorName == other.showCreatorName &&
            visibilityAudience == other.visibilityAudience &&
            eventVisibility == other.eventVisibility &&
            eventCapacityText == other.eventCapacityText &&
            approvalRequired == other.approvalRequired &&
            guestListVisibility == other.guestListVisibility &&
            stagedPhotoMime == other.stagedPhotoMime &&
            isSubmitting == other.isSubmitting &&
            stagedPhotoBytes.contentEquals(other.stagedPhotoBytes)
    }

    override fun hashCode(): Int {
        var result = category.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + soundtrackUrl.hashCode()
        result = 31 * result + expiration.hashCode()
        result = 31 * result + eventSchedule.hashCode()
        result = 31 * result + (eventScheduleError?.hashCode() ?: 0)
        result = 31 * result + eventCategories.hashCode()
        result = 31 * result + (repeatFrequency?.hashCode() ?: 0)
        result = 31 * result + occurrences
        result = 31 * result + venueScale.hashCode()
        result = 31 * result + (submitValidationError?.hashCode() ?: 0)
        result = 31 * result + addressQuery.hashCode()
        result = 31 * result + addressSuggestions.hashCode()
        result = 31 * result + (selectedEventLocation?.hashCode() ?: 0)
        result = 31 * result + addressSearching.hashCode()
        result = 31 * result + resolvingCurrentLocation.hashCode()
        result = 31 * result + showCreatorName.hashCode()
        result = 31 * result + visibilityAudience.hashCode()
        result = 31 * result + eventVisibility.hashCode()
        result = 31 * result + eventCapacityText.hashCode()
        result = 31 * result + approvalRequired.hashCode()
        result = 31 * result + guestListVisibility.hashCode()
        result = 31 * result + (stagedPhotoBytes?.contentHashCode() ?: 0)
        result = 31 * result + (stagedPhotoMime?.hashCode() ?: 0)
        result = 31 * result + isSubmitting.hashCode()
        return result
    }
}

class CreateBeaconViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CreateBeaconUiState())
    val uiState: StateFlow<CreateBeaconUiState> = _uiState.asStateFlow()

    fun setCategory(category: BeaconDropCategory) {
        _uiState.update { it.copy(category = category, submitValidationError = null) }
    }

    fun setTitle(title: String) {
        _uiState.update { it.copy(title = title, submitValidationError = null) }
    }

    fun setDescription(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun setSoundtrackUrl(url: String) {
        _uiState.update { it.copy(soundtrackUrl = url, submitValidationError = null) }
    }

    fun setExpiration(expiration: BeaconDuration) {
        _uiState.update { it.copy(expiration = expiration) }
    }

    fun setEventSchedule(
        schedule: EventSchedule,
        error: EventScheduleValidationError?,
    ) {
        _uiState.update { it.copy(eventSchedule = schedule, eventScheduleError = error) }
    }

    fun setEventCategories(categories: Set<String>) {
        _uiState.update { it.copy(eventCategories = categories) }
    }

    fun setRepeatFrequency(frequency: EventRecurrenceFrequency?) {
        _uiState.update { it.copy(repeatFrequency = frequency) }
    }

    fun setOccurrences(count: Int) {
        _uiState.update { it.copy(occurrences = count.coerceIn(EVENT_OCCURRENCE_RANGE)) }
    }

    fun setVenueScale(scale: EventVenueScale) {
        _uiState.update { it.copy(venueScale = scale) }
    }

    fun setSubmitValidationError(message: String?) {
        _uiState.update { it.copy(submitValidationError = message) }
    }

    fun setAddressQuery(query: String) {
        _uiState.update { it.copy(addressQuery = query) }
    }

    fun setAddressSuggestions(suggestions: List<GeocodedPlace>) {
        _uiState.update { it.copy(addressSuggestions = suggestions) }
    }

    fun setSelectedEventLocation(place: GeocodedPlace?) {
        _uiState.update { it.copy(selectedEventLocation = place) }
    }

    fun setAddressSearching(searching: Boolean) {
        _uiState.update { it.copy(addressSearching = searching) }
    }

    fun setResolvingCurrentLocation(resolving: Boolean) {
        _uiState.update { it.copy(resolvingCurrentLocation = resolving) }
    }

    fun setShowCreatorName(show: Boolean) {
        _uiState.update { it.copy(showCreatorName = show) }
    }

    fun setVisibilityAudience(audience: BeaconVisibilityAudience) {
        _uiState.update { it.copy(visibilityAudience = audience) }
    }

    fun setEventVisibility(visibility: EventVisibility) {
        _uiState.update { state ->
            val nextAudience =
                when (visibility) {
                    EventVisibility.PUBLIC -> state.visibilityAudience
                    EventVisibility.UNLISTED, EventVisibility.INVITE_ONLY ->
                        if (state.visibilityAudience == BeaconVisibilityAudience.EVERYONE) {
                            BeaconVisibilityAudience.CONNECTIONS
                        } else {
                            state.visibilityAudience
                        }
                }
            state.copy(
                eventVisibility = visibility,
                visibilityAudience = nextAudience,
            )
        }
    }

    fun setEventCapacityText(text: String) {
        _uiState.update { it.copy(eventCapacityText = text.filter { ch -> ch.isDigit() }.take(6)) }
    }

    fun setApprovalRequired(required: Boolean) {
        _uiState.update { it.copy(approvalRequired = required) }
    }

    fun setGuestListVisibility(visibility: GuestListVisibility) {
        _uiState.update { it.copy(guestListVisibility = visibility) }
    }

    fun setStagedPhoto(
        bytes: ByteArray?,
        mime: String?,
    ) {
        _uiState.update {
            it.copy(
                stagedPhotoBytes = bytes,
                stagedPhotoMime = mime,
                submitValidationError = null,
            )
        }
    }

    fun clearStagedPhoto() {
        setStagedPhoto(null, null)
    }

    fun setSubmitting(submitting: Boolean) {
        _uiState.update { it.copy(isSubmitting = submitting) }
    }

    /**
     * The sheet closed without posting (swipe, back, scrim): keep the draft so reopening picks
     * up where the user left off, but clear in-flight UI whose coroutines died with the sheet.
     */
    fun onSheetDismissed() {
        _uiState.update {
            it.copy(
                addressSearching = false,
                resolvingCurrentLocation = false,
                submitValidationError = null,
            )
        }
    }

    /** Call once the beacon is posted so the next open starts blank. */
    fun reset() {
        _uiState.value = CreateBeaconUiState()
    }
}
