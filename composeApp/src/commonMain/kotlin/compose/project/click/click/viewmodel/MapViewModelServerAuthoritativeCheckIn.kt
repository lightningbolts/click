package compose.project.click.click.viewmodel // pragma: allowlist secret

import androidx.lifecycle.viewModelScope
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.api.BeaconEngagementHttpException // pragma: allowlist secret
import compose.project.click.click.events.beaconCheckInFailureMessage // pragma: allowlist secret
import compose.project.click.click.events.resolveEventCheckInRadiusMeters // pragma: allowlist secret
import compose.project.click.click.ui.utils.haversineDistance // pragma: allowlist secret
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Server-authoritative event presence mutation.
 *
 * Unlike the legacy PR #99 path, this never synthesizes a successful check-in locally:
 * - the last confirmed state remains visible while the request is pending;
 * - HTTP 409 remains a failure (event not live yet), even inside the geofence;
 * - checkout only changes physical-presence state and does not revoke RSVP-backed hub access;
 * - success haptics fire only after the server confirms the mutation.
 *
 * This is intentionally separate from bookmark optimism: bookmarks are reversible UI state,
 * while event presence is an authorization-adjacent server fact.
 */
internal fun MapViewModel.toggleBeaconCheckInServerAuthoritativeImpl(beaconId: String) {
    val id = beaconId.trim()
    if (id.isEmpty() || id in _beaconCheckInPendingIds.value) return

    val previous = _beaconEngagementById.value[id]
    val currentlyCheckedIn = previous?.checkedIn == true
    _beaconCheckInPendingIds.update { it + id }

    if (currentlyCheckedIn) {
        viewModelScope.launch {
            if (!ensureClickWebAuthReady()) {
                _beaconCheckInPendingIds.update { it - id }
                _engagementSnackbar.value = "Please sign in again to update check-in"
                return@launch
            }

            mapBeaconRepository.checkOutBeacon(id).fold(
                onSuccess = { payload ->
                    earlyCheckInBeaconIds -= id
                    updateBeaconEngagementCache { current ->
                        val base = current[id] ?: previous ?: BeaconEngagementCacheEntry()
                        current + (
                            id to
                                base.copy(
                                    checkedIn = payload.checkedIn,
                                    checkedInAt = payload.checkedInAt,
                                    checkInCount = payload.checkInCount,
                                    localEarlyCheckIn = false,
                                    hubId = payload.hubId?.trim()?.takeIf { it.isNotEmpty() } ?: base.hubId,
                                )
                        )
                    }
                    payload.hubId?.trim()?.takeIf { it.isNotEmpty() }?.let { applyBeaconHubId(id, it) }
                    _beaconCheckInPendingIds.update { it - id }
                    invalidateBeaconAttendeeDirectory(id)
                    if (_beaconRsvpById.value[id]?.currentUserSignedUp == true) {
                        loadBeaconAttendeeDirectory(id, forceRefresh = true)
                    }
                    PlatformHapticsPolicy.successNotification()
                    _engagementSnackbar.value = "Checked out"
                },
                onFailure = { error ->
                    _beaconCheckInPendingIds.update { it - id }
                    val http = error as? BeaconEngagementHttpException
                    _engagementSnackbar.value =
                        beaconCheckInFailureMessage(
                            httpStatus = http?.status,
                            fallback = http?.message ?: "Couldn't undo check-in",
                        )
                },
            )
        }
        return
    }

    viewModelScope.launch {
        if (!locationService.hasLocationPermission()) {
            _beaconCheckInPendingIds.update { it - id }
            _engagementSnackbar.value = "Location access is required to check in"
            return@launch
        }

        val loc = resolveBeaconDropLocation()
        if (
            loc == null ||
            !loc.latitude.isFinite() ||
            !loc.longitude.isFinite() ||
            (loc.latitude == 0.0 && loc.longitude == 0.0)
        ) {
            _beaconCheckInPendingIds.update { it - id }
            _engagementSnackbar.value = "Location required to check in"
            return@launch
        }

        val beacon =
            _mapBeacons.value.firstOrNull { it.id == id }
                ?: (_selection.value as? MapSelection.BeaconSelected)?.beacon?.takeIf { it.id == id }
        if (beacon != null) {
            val distanceM =
                haversineDistance(
                    loc.latitude,
                    loc.longitude,
                    beacon.latitude,
                    beacon.longitude,
                )
            if (distanceM > beacon.resolveEventCheckInRadiusMeters()) {
                _beaconCheckInPendingIds.update { it - id }
                _engagementSnackbar.value = "Move closer to the event to check in"
                return@launch
            }
        }

        if (!ensureClickWebAuthReady()) {
            _beaconCheckInPendingIds.update { it - id }
            _engagementSnackbar.value = "Please sign in again to check in"
            return@launch
        }

        mapBeaconRepository
            .checkInBeacon(
                id,
                engagementTelemetry(latitude = loc.latitude, longitude = loc.longitude),
            ).fold(
                onSuccess = { payload ->
                    // Only a server-confirmed checkedIn=true may transition the UI into checked-in.
                    updateBeaconEngagementCache { current ->
                        val base = current[id] ?: previous ?: BeaconEngagementCacheEntry()
                        current + (
                            id to
                                base.copy(
                                    checkedIn = payload.checkedIn,
                                    checkedInAt = payload.checkedInAt,
                                    checkInCount = payload.checkInCount,
                                    localEarlyCheckIn = false,
                                    hubId = payload.hubId?.trim()?.takeIf { it.isNotEmpty() } ?: base.hubId,
                                )
                        )
                    }
                    earlyCheckInBeaconIds -= id
                    payload.hubId?.trim()?.takeIf { it.isNotEmpty() }?.let { applyBeaconHubId(id, it) }
                    _beaconCheckInPendingIds.update { it - id }
                    invalidateBeaconAttendeeDirectory(id)
                    if (payload.checkedIn || _beaconRsvpById.value[id]?.currentUserSignedUp == true) {
                        loadBeaconAttendeeDirectory(id, forceRefresh = true)
                    }
                    if (payload.checkedIn) {
                        PlatformHapticsPolicy.successNotification()
                        _engagementSnackbar.value = "Checked in"
                    } else {
                        _engagementSnackbar.value = "Check-in was not confirmed"
                    }
                },
                onFailure = { error ->
                    // Preserve the previous confirmed state. In particular, 409 is not converted
                    // into a local early check-in and never grants event-presence semantics.
                    _beaconCheckInPendingIds.update { it - id }
                    val http = error as? BeaconEngagementHttpException
                    _engagementSnackbar.value =
                        beaconCheckInFailureMessage(
                            httpStatus = http?.status,
                            fallback = http?.message,
                        )
                },
            )
    }
}
