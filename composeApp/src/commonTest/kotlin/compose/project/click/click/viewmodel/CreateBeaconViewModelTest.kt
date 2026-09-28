package compose.project.click.click.viewmodel // pragma: allowlist secret

import compose.project.click.click.data.models.BeaconVisibilityAudience // pragma: allowlist secret
import compose.project.click.click.events.EVENT_OCCURRENCE_RANGE // pragma: allowlist secret
import compose.project.click.click.events.EventRecurrence // pragma: allowlist secret
import compose.project.click.click.events.EventRecurrenceFrequency // pragma: allowlist secret
import compose.project.click.click.events.EventSchedule // pragma: allowlist secret
import compose.project.click.click.events.EventVisibility // pragma: allowlist secret
import compose.project.click.click.events.GuestListVisibility // pragma: allowlist secret
import compose.project.click.click.events.eventRecurrenceValidationError // pragma: allowlist secret
import compose.project.click.click.ui.screens.BeaconDropCategory // pragma: allowlist secret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreateBeaconViewModelTest {
    @Test
    fun stagedPhotoDoesNotResetCategory() {
        val vm = CreateBeaconViewModel()
        vm.setCategory(BeaconDropCategory.EVENT)
        vm.setTitle("Park hang")
        vm.setStagedPhoto(byteArrayOf(1, 2, 3), "image/jpeg")

        val state = vm.uiState.value
        assertEquals(BeaconDropCategory.EVENT, state.category)
        assertEquals("Park hang", state.title)
        assertTrue(state.hasStagedPhoto)
        assertEquals("image/jpeg", state.stagedPhotoMime)
        assertEquals(listOf<Byte>(1, 2, 3), state.stagedPhotoBytes?.toList())
    }

    @Test
    fun replaceAndRemovePhotoKeepDrafts() {
        val vm = CreateBeaconViewModel()
        vm.setCategory(BeaconDropCategory.HAZARD)
        vm.setTitle("Icy sidewalk")
        vm.setStagedPhoto(byteArrayOf(9), "image/jpeg")
        vm.setStagedPhoto(byteArrayOf(8, 8), "image/jpeg")
        assertEquals(
            listOf<Byte>(8, 8),
            vm.uiState.value.stagedPhotoBytes
                ?.toList(),
        )
        assertEquals(BeaconDropCategory.HAZARD, vm.uiState.value.category)

        vm.clearStagedPhoto()
        assertNull(vm.uiState.value.stagedPhotoBytes)
        assertEquals("Icy sidewalk", vm.uiState.value.title)
        assertEquals(BeaconDropCategory.HAZARD, vm.uiState.value.category)
    }

    @Test
    fun resetClearsFormForTheNextDrop() {
        val vm = CreateBeaconViewModel()
        vm.setCategory(BeaconDropCategory.STUDY)
        vm.setTitle("Library")
        vm.setStagedPhoto(byteArrayOf(1), "image/jpeg")
        vm.reset()
        val state = vm.uiState.value
        assertEquals(BeaconDropCategory.SOUNDTRACK, state.category)
        assertEquals("", state.title)
        assertNull(state.stagedPhotoBytes)
    }

    @Test
    fun setEventVisibility_autoBindsMapAudienceForPrivateListings() {
        val vm = CreateBeaconViewModel()
        vm.setEventVisibility(EventVisibility.UNLISTED)
        assertEquals(EventVisibility.UNLISTED, vm.uiState.value.eventVisibility)
        assertEquals(BeaconVisibilityAudience.CONNECTIONS, vm.uiState.value.visibilityAudience)

        vm.setVisibilityAudience(BeaconVisibilityAudience.CORE_CONNECTIONS)
        vm.setEventVisibility(EventVisibility.INVITE_ONLY)
        assertEquals(BeaconVisibilityAudience.CORE_CONNECTIONS, vm.uiState.value.visibilityAudience)

        vm.setVisibilityAudience(BeaconVisibilityAudience.EVERYONE)
        vm.setEventVisibility(EventVisibility.PUBLIC)
        assertEquals(BeaconVisibilityAudience.EVERYONE, vm.uiState.value.visibilityAudience)
    }

    @Test
    fun eventListingFieldsPersistAcrossDraftEdits() {
        val vm = CreateBeaconViewModel()
        vm.setEventVisibility(EventVisibility.UNLISTED)
        vm.setEventCapacityText("50")
        vm.setApprovalRequired(true)
        vm.setGuestListVisibility(GuestListVisibility.HOSTS_ONLY)
        val state = vm.uiState.value
        assertEquals("50", state.eventCapacityText)
        assertTrue(state.approvalRequired)
        assertEquals(GuestListVisibility.HOSTS_ONLY, state.guestListVisibility)
    }

    @Test
    fun dismissingTheSheetKeepsTheDraftButClearsInFlightUi() {
        val vm = CreateBeaconViewModel()
        vm.setCategory(BeaconDropCategory.EVENT)
        vm.setTitle("Trivia night")
        vm.setRepeatFrequency(EventRecurrenceFrequency.WEEKLY)
        vm.setAddressSearching(true)
        vm.setResolvingCurrentLocation(true)
        vm.setSubmitValidationError("Please add a title.")
        vm.onSheetDismissed()
        val state = vm.uiState.value
        assertEquals(BeaconDropCategory.EVENT, state.category)
        assertEquals("Trivia night", state.title)
        assertEquals(EventRecurrenceFrequency.WEEKLY, state.repeatFrequency)
        assertFalse(state.addressSearching)
        assertFalse(state.resolvingCurrentLocation)
        assertNull(state.submitValidationError)
    }

    @Test
    fun recurrenceBodyFollowsFrequencyAndClampedCount() {
        val vm = CreateBeaconViewModel()
        assertNull(vm.uiState.value.eventRecurrence)
        vm.setRepeatFrequency(EventRecurrenceFrequency.BIWEEKLY)
        vm.setOccurrences(100)
        assertEquals(EventRecurrence("biweekly", EVENT_OCCURRENCE_RANGE.last), vm.uiState.value.eventRecurrence)
        vm.setOccurrences(0)
        assertEquals(EVENT_OCCURRENCE_RANGE.first, vm.uiState.value.occurrences)
        vm.setRepeatFrequency(null)
        assertNull(vm.uiState.value.eventRecurrence)
    }

    @Test
    fun repeatingEventsMayNotOverlap() {
        val hour = 60L * 60_000L
        val day = EventSchedule(startEpochMs = 0L, endEpochMs = 24 * hour)
        assertNull(eventRecurrenceValidationError(day, EventRecurrenceFrequency.DAILY))
        assertNull(eventRecurrenceValidationError(day.copy(endEpochMs = 72 * hour), null))
        assertNotNull(eventRecurrenceValidationError(day.copy(endEpochMs = 25 * hour), EventRecurrenceFrequency.DAILY))
        assertNotNull(eventRecurrenceValidationError(day.copy(endEpochMs = 8 * 24 * hour), EventRecurrenceFrequency.WEEKLY))
    }
}
