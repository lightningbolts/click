@file:Suppress(
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.viewmodel // pragma: allowlist secret

import androidx.lifecycle.viewModelScope
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.api.BeaconAttendeeDto // pragma: allowlist secret
import compose.project.click.click.data.api.BeaconEngagementHttpException // pragma: allowlist secret
import compose.project.click.click.data.api.EngagementTelemetryBody // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.storage.BeaconEngagementPersistence // pragma: allowlist secret
import compose.project.click.click.data.storage.BeaconRsvpPersistence // pragma: allowlist secret
import compose.project.click.click.events.EventRsvpRequestStatus // pragma: allowlist secret
import compose.project.click.click.events.beaconCheckInFailureMessage // pragma: allowlist secret
import compose.project.click.click.events.eventSchedule // pragma: allowlist secret
import compose.project.click.click.events.normalizeEventRsvpErrorMessage // pragma: allowlist secret
import compose.project.click.click.events.resolveEventCheckInRadiusMeters // pragma: allowlist secret
import compose.project.click.click.getPlatform // pragma: allowlist secret
import compose.project.click.click.ui.utils.displayDynamicTitle // pragma: allowlist secret
import compose.project.click.click.ui.utils.haversineDistance // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

internal suspend fun MapViewModel.hydrateBeaconRsvpFromDisk(userId: String? = null) {
    val uid =
        userId?.trim()?.takeIf { it.isNotEmpty() }
            ?: SupabaseConfig.client.auth
                .currentUserOrNull()
                ?.id
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            ?: AppDataManager.currentUser.value
                ?.id
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            ?: return
    val restored = BeaconRsvpPersistence.load(tokenStorage, uid)
    if (restored.isEmpty()) return
    _beaconRsvpById.update { current -> current + restored }
}

internal fun MapViewModel.persistBeaconRsvpCache() {
    viewModelScope.launch {
        val uid =
            SupabaseConfig.client.auth
                .currentUserOrNull()
                ?.id
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: AppDataManager.currentUser.value
                    ?.id
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                ?: return@launch
        BeaconRsvpPersistence.save(tokenStorage, uid, _beaconRsvpById.value)
    }
}

internal fun MapViewModel.updateBeaconRsvpCache(transform: (Map<String, BeaconRsvpCacheEntry>) -> Map<String, BeaconRsvpCacheEntry>) {
    _beaconRsvpById.update(transform)
    persistBeaconRsvpCache()
    AppDataManager.notifyEventEngagementChanged()
}

internal suspend fun MapViewModel.hydrateBeaconEngagementFromDisk(userId: String? = null) {
    val uid =
        userId?.trim()?.takeIf { it.isNotEmpty() }
            ?: SupabaseConfig.client.auth
                .currentUserOrNull()
                ?.id
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            ?: AppDataManager.currentUser.value
                ?.id
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            ?: return
    val restored = BeaconEngagementPersistence.load(tokenStorage, uid)
    if (restored.isEmpty()) return
    restored.forEach { (id, entry) ->
        if (entry.localEarlyCheckIn) {
            earlyCheckInBeaconIds += id
        }
    }
    _beaconEngagementById.update { current ->
        // Prefer disk early/checked-in over a stale in-memory "not checked in" from a racing fetch.
        current +
            restored.mapValues { (id, disk) ->
                val mem = current[id]
                if (disk.localEarlyCheckIn || disk.checkedIn) {
                    disk
                } else if (mem?.localEarlyCheckIn == true || id in earlyCheckInBeaconIds) {
                    mem?.copy(checkedIn = true, localEarlyCheckIn = true) ?: disk.copy(
                        checkedIn = true,
                        localEarlyCheckIn = true,
                    )
                } else {
                    disk
                }
            }
    }
}

internal fun MapViewModel.persistBeaconEngagementCache() {
    val generation = ++engagementPersistGeneration
    viewModelScope.launch {
        engagementPersistMutex.withLock {
            if (generation != engagementPersistGeneration) return@withLock
            val uid =
                SupabaseConfig.client.auth
                    .currentUserOrNull()
                    ?.id
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: AppDataManager.currentUser.value
                        ?.id
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                    ?: return@withLock
            BeaconEngagementPersistence.save(tokenStorage, uid, _beaconEngagementById.value)
        }
    }
}

internal fun MapViewModel.updateBeaconEngagementCache(
    persistDisk: Boolean = true,
    transform: (Map<String, BeaconEngagementCacheEntry>) -> Map<String, BeaconEngagementCacheEntry>,
) {
    _beaconEngagementById.update(transform)
    if (persistDisk) {
        persistBeaconEngagementCache()
        AppDataManager.notifyEventEngagementChanged()
    }
}

internal fun MapViewModel.mergeEngagementFromServer(
    existing: BeaconEngagementCacheEntry?,
    beaconId: String,
    bookmarked: Boolean,
    checkedIn: Boolean,
    checkedInAt: String?,
    checkInCount: Int,
    preferServer: Boolean = false,
    hubId: String? = null,
): BeaconEngagementCacheEntry {
    // On force refresh, trust the server so a device-local early check-in (or poisoned
    // far-away optimistic state) cannot override a real server row across kills/devices.
    val keepEarly =
        !preferServer &&
            !checkedIn &&
            (
                existing?.localEarlyCheckIn == true ||
                    beaconId in earlyCheckInBeaconIds
            )
    if (preferServer && !checkedIn) {
        earlyCheckInBeaconIds -= beaconId
    }
    return BeaconEngagementCacheEntry(
        bookmarked = bookmarked,
        checkedIn = checkedIn || keepEarly,
        checkedInAt = checkedInAt ?: existing?.checkedInAt,
        checkInCount = checkInCount,
        localEarlyCheckIn = keepEarly,
        hubId = hubId?.trim()?.takeIf { it.isNotEmpty() } ?: existing?.hubId,
    )
}

internal fun MapViewModel.engagementTelemetry(
    latitude: Double? = null,
    longitude: Double? = null,
    surface: String? = null,
    bookmarked: Boolean? = null,
): EngagementTelemetryBody {
    val platform =
        getPlatform().name.lowercase().let { name ->
            when {
                name.contains("android") -> "android"
                name.contains("ios") || name.contains("iphone") -> "ios"
                else -> name.take(32)
            }
        }
    return EngagementTelemetryBody(
        latitude = latitude,
        longitude = longitude,
        source = "mobile",
        platform = platform,
        surface = surface,
        bookmarked = bookmarked,
    )
}

/**
 * Loads RSVP attendees + signed-up state from click-web. Waits for the Supabase session so
 * cold starts (app switcher kill) do not hit the API before JWT restore and cache a false
 * "not signed up" sentinel.
 */
internal fun MapViewModel.loadBeaconRsvpImpl(
    beaconId: String,
    forceRefresh: Boolean = false,
) {
    val id = beaconId.trim()
    if (id.isEmpty()) return
    viewModelScope.launch(Dispatchers.Default) {
        if (!ensureClickWebAuthReady()) return@launch
        if (!forceRefresh && _beaconRsvpById.value.containsKey(id)) return@launch
        if (id in _beaconRsvpPendingIds.value) return@launch

        _beaconRsvpLoadingIds.update { it + id }
        try {
            mapBeaconRepository.fetchBeaconRsvp(id).fold(
                onSuccess = { payload ->
                    if (id in _beaconRsvpPendingIds.value) return@fold
                    updateBeaconRsvpCache { current ->
                        current + (
                            id to
                                BeaconRsvpCacheEntry(
                                    attendees = payload.attendees,
                                    currentUserSignedUp = payload.currentUserSignedUp,
                                    requestStatus = EventRsvpRequestStatus.fromRaw(payload.requestStatus),
                                )
                        )
                    }
                },
                onFailure = {
                    // Keep disk-hydrated cache on failure; do not write a false-negative entry.
                },
            )
        } finally {
            _beaconRsvpLoadingIds.update { it - id }
        }
    }
}

/**
 * Loads enriched people directory (interests, FoF mutuals, RSVP distance).
 * Requires viewer RSVP or check-in (403 otherwise — treated as empty/locked).
 */
internal fun MapViewModel.loadBeaconAttendeeDirectoryImpl(
    beaconId: String,
    forceRefresh: Boolean = false,
) {
    val id = beaconId.trim()
    if (id.isEmpty()) return
    viewModelScope.launch(Dispatchers.Default) {
        if (!ensureClickWebAuthReady()) return@launch
        if (!forceRefresh && _beaconDirectoryById.value.containsKey(id)) return@launch
        if (id in _beaconDirectoryLoadingIds.value) return@launch

        _beaconDirectoryLoadingIds.update { it + id }
        try {
            mapBeaconRepository.fetchBeaconAttendeeDirectory(id).fold(
                onSuccess = { payload ->
                    val mapped =
                        payload.attendees.map { dto ->
                            compose.project.click.click.events.DirectoryAttendee(
                                userId = dto.userId,
                                name = dto.name,
                                avatarUrl = dto.avatarUrl,
                                signedUpAt = dto.signedUpAt,
                                distanceMeters = dto.distanceMeters,
                                sharedInterests = dto.sharedInterests,
                                sharedInterestCount = dto.sharedInterestCount.coerceAtLeast(0),
                                relationship =
                                    compose.project.click.click.events.AttendeeRelationship
                                        .fromApi(dto.relationship),
                                mutualVia =
                                    dto.mutualVia.map {
                                        compose.project.click.click.events
                                            .MutualViaPeer(it.userId, it.name)
                                    },
                                mutualConnectionCount = dto.mutualConnectionCount.coerceAtLeast(0),
                            )
                        }
                    _beaconDirectoryById.update { current ->
                        current + (
                            id to
                                BeaconDirectoryCacheEntry(
                                    attendees = mapped,
                                    currentUserSignedUp = payload.currentUserSignedUp,
                                    currentUserCheckedIn = payload.currentUserCheckedIn,
                                    mutualsSectionUnlocked = payload.mutualsSectionUnlocked,
                                )
                        )
                    }
                },
                onFailure = {
                    // Keep prior cache; directory may be locked until RSVP/check-in.
                },
            )
        } finally {
            _beaconDirectoryLoadingIds.update { it - id }
        }
    }
}

internal fun MapViewModel.invalidateBeaconAttendeeDirectory(beaconId: String) {
    _beaconDirectoryById.update { it - beaconId }
}

internal fun MapViewModel.currentUserAsAttendee(): BeaconAttendeeDto? {
    val user = AppDataManager.currentUser.value ?: return null
    return BeaconAttendeeDto(
        userId = user.id,
        name = user.name?.trim()?.takeIf { it.isNotEmpty() } ?: "You",
        avatarUrl = user.image,
    )
}

internal fun MapViewModel.applyOptimisticRsvp(
    beaconId: String,
    signedUp: Boolean,
) {
    val userId = AppDataManager.currentUser.value?.id ?: return
    updateBeaconRsvpCache { current ->
        val prev = current[beaconId]
        if (signedUp) {
            val attendee = currentUserAsAttendee() ?: return@updateBeaconRsvpCache current
            val mergedAttendees =
                (
                    prev
                        ?.attendees
                        .orEmpty()
                        .filterNot { it.userId == attendee.userId } + attendee
                ).distinctBy { it.userId }
            current + (
                beaconId to
                    BeaconRsvpCacheEntry(
                        attendees = mergedAttendees,
                        currentUserSignedUp = true,
                    )
            )
        } else {
            val remaining = prev?.attendees.orEmpty().filterNot { it.userId == userId }
            current + (
                beaconId to
                    BeaconRsvpCacheEntry(
                        attendees = remaining,
                        currentUserSignedUp = false,
                    )
            )
        }
    }
}

internal fun MapViewModel.restoreRsvpSnapshot(
    beaconId: String,
    previous: BeaconRsvpCacheEntry?,
) {
    updateBeaconRsvpCache { current ->
        when (previous) {
            null -> current - beaconId
            else -> current + (beaconId to previous)
        }
    }
}

internal fun MapViewModel.loadBeaconEngagementImpl(
    beaconId: String,
    forceRefresh: Boolean = false,
) {
    val id = beaconId.trim()
    if (id.isEmpty()) return
    viewModelScope.launch(Dispatchers.Default) {
        if (!ensureClickWebAuthReady()) return@launch
        if (!forceRefresh && _beaconEngagementById.value.containsKey(id)) return@launch
        if (id in _beaconEngagementPendingIds.value) return@launch
        mapBeaconRepository.fetchBeaconEngagement(id).fold(
            onSuccess = { payload ->
                if (id in _beaconEngagementPendingIds.value) return@fold
                updateBeaconEngagementCache { current ->
                    val existing = current[id]
                    current + (
                        id to
                            mergeEngagementFromServer(
                                existing = existing,
                                beaconId = id,
                                bookmarked = payload.bookmarked,
                                checkedIn = payload.checkedIn,
                                checkedInAt = payload.checkedInAt,
                                checkInCount = payload.checkInCount,
                                preferServer = forceRefresh,
                                hubId = payload.hubId,
                            )
                    )
                }
                payload.hubId
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { applyBeaconHubId(id, it) }
            },
            onFailure = { /* keep disk cache */ },
        )
    }
}

/** Pull server engagement for visible event pins so check-ins sync across devices after cold start. */
internal fun MapViewModel.hydrateEventEngagementFromServerImpl() {
    viewModelScope.launch(Dispatchers.Default) {
        if (!ensureClickWebAuthReady()) return@launch
        val eventIds =
            _mapBeacons.value
                .asSequence()
                .filter { it.kind == MapBeaconKind.EVENT }
                .map { it.id }
                .distinct()
                .take(40)
                .toList()
        for (id in eventIds) {
            if (id in _beaconEngagementPendingIds.value) continue
            mapBeaconRepository.fetchBeaconEngagement(id).fold(
                onSuccess = { payload ->
                    if (id in _beaconEngagementPendingIds.value) return@fold
                    updateBeaconEngagementCache { current ->
                        current + (
                            id to
                                mergeEngagementFromServer(
                                    existing = current[id],
                                    beaconId = id,
                                    bookmarked = payload.bookmarked,
                                    checkedIn = payload.checkedIn,
                                    checkedInAt = payload.checkedInAt,
                                    checkInCount = payload.checkInCount,
                                    preferServer = true,
                                    hubId = payload.hubId,
                                )
                        )
                    }
                    payload.hubId
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { applyBeaconHubId(id, it) }
                },
                onFailure = { /* ignore per-beacon */ },
            )
        }
    }
}

internal fun MapViewModel.recordEventImpressionImpl(beaconId: String) {
    val id = beaconId.trim()
    if (id.isEmpty()) return
    viewModelScope.launch {
        if (!ensureClickWebAuthReady()) return@launch
        mapBeaconRepository.recordBeaconImpression(
            id,
            engagementTelemetry(surface = "detail"),
        )
    }
}

internal fun MapViewModel.recordEventShareImpl(
    beaconId: String,
    shareUrl: String? = null,
) {
    val id = beaconId.trim()
    if (id.isEmpty()) return
    viewModelScope.launch {
        if (!ensureClickWebAuthReady()) return@launch
        mapBeaconRepository.recordBeaconShare(
            id,
            engagementTelemetry(surface = "detail"),
            shareUrl = shareUrl,
        )
    }
}

internal fun MapViewModel.toggleBeaconBookmarkImpl(beaconId: String) {
    val id = beaconId.trim()
    if (id.isEmpty() || id in _beaconBookmarkPendingIds.value) return
    val previous = _beaconEngagementById.value[id]
    val nextBookmarked = !(previous?.bookmarked ?: false)
    _beaconBookmarkPendingIds.update { it + id }
    updateBeaconEngagementCache { current ->
        val base = current[id] ?: BeaconEngagementCacheEntry()
        current + (id to base.copy(bookmarked = nextBookmarked))
    }
    // Optimistic Home "Saved events" update — do not wait for network or app restart.
    applyOptimisticCachedBookmark(id, nextBookmarked)
    PlatformHapticsPolicy.successNotification()
    viewModelScope.launch {
        if (!ensureClickWebAuthReady()) {
            restoreEngagementSnapshot(id, previous)
            applyOptimisticCachedBookmark(id, previous?.bookmarked == true)
            _beaconBookmarkPendingIds.update { it - id }
            _engagementSnackbar.value = "Sign-in still loading — try bookmark again"
            return@launch
        }
        mapBeaconRepository
            .setBeaconBookmark(
                id,
                nextBookmarked,
                engagementTelemetry(bookmarked = nextBookmarked),
            ).fold(
                onSuccess = {
                    _beaconBookmarkPendingIds.update { it - id }
                    // Reconcile after PUT lands so Home list isn't wiped by a premature GET.
                    reconcileCachedEventBookmarksFromServer()
                },
                onFailure = { e ->
                    restoreEngagementSnapshot(id, previous)
                    applyOptimisticCachedBookmark(id, previous?.bookmarked == true)
                    _beaconBookmarkPendingIds.update { it - id }
                    _engagementSnackbar.value =
                        e.message?.takeIf { it.isNotBlank() } ?: "Couldn't update bookmark"
                },
            )
    }
}

internal fun MapViewModel.applyOptimisticCachedBookmark(
    beaconId: String,
    bookmarked: Boolean,
) {
    if (bookmarked) {
        val beacon = _mapBeacons.value.firstOrNull { it.id == beaconId }
        val schedule = beacon?.eventSchedule()
        val item =
            compose.project.click.click.data.api.EventBookmarkItemDto(
                beaconId = beaconId,
                bookmarkedAt = Clock.System.now().toString(),
                title = beacon?.displayDynamicTitle(),
                eventStartAt =
                    schedule?.let {
                        kotlinx.datetime.Instant
                            .fromEpochMilliseconds(it.startEpochMs)
                            .toString()
                    },
                eventEndAt =
                    schedule?.let {
                        kotlinx.datetime.Instant
                            .fromEpochMilliseconds(it.endEpochMs)
                            .toString()
                    },
                locationName = beacon?.metadata?.locationName,
                formattedAddress = beacon?.metadata?.formattedAddress,
                eventCategories = beacon?.metadata?.eventCategories.orEmpty(),
                latitude = beacon?.latitude,
                longitude = beacon?.longitude,
                expiresAt =
                    beacon?.expiresAtEpochMs?.let {
                        kotlinx.datetime.Instant
                            .fromEpochMilliseconds(it)
                            .toString()
                    },
                creatorId = beacon?.createdByUserId,
                creatorName = beacon?.creatorDisplayName,
                createdAt =
                    beacon?.createdAtEpochMs?.let {
                        kotlinx.datetime.Instant
                            .fromEpochMilliseconds(it)
                            .toString()
                    },
                showCreatorName = beacon?.showCreatorName == true,
            )
        val merged =
            (listOf(item) + AppDataManager.cachedEventBookmarks.value.filterNot { it.beaconId == beaconId })
                .distinctBy { it.beaconId }
        AppDataManager.updateCachedEventBookmarks(merged)
    } else {
        AppDataManager.updateCachedEventBookmarks(
            AppDataManager.cachedEventBookmarks.value.filterNot { it.beaconId == beaconId },
        )
    }
}

internal fun MapViewModel.reconcileCachedEventBookmarksFromServer() {
    viewModelScope.launch(Dispatchers.Default) {
        mapBeaconRepository.fetchMyEventBookmarks().onSuccess { remote ->
            // Avoid wiping Home "Saved" when a flaky empty response races an optimistic save.
            if (remote.bookmarks.isEmpty() && AppDataManager.cachedEventBookmarks.value.isNotEmpty()) {
                return@onSuccess
            }
            AppDataManager.updateCachedEventBookmarks(remote.bookmarks)
        }
    }
}

internal fun MapViewModel.toggleBeaconCheckInImpl(beaconId: String) {
    val id = beaconId.trim()
    if (id.isEmpty() || id in _beaconCheckInPendingIds.value) return
    val previous = _beaconEngagementById.value[id]
    val currentlyCheckedIn = previous?.checkedIn == true
    if (currentlyCheckedIn) {
        earlyCheckInBeaconIds -= id
        _beaconCheckInPendingIds.update { it + id }
        updateBeaconEngagementCache { current ->
            val base = current[id] ?: BeaconEngagementCacheEntry()
            current + (id to base.copy(checkedIn = false, checkedInAt = null, localEarlyCheckIn = false))
        }
        PlatformHapticsPolicy.successNotification()
        viewModelScope.launch {
            if (!ensureClickWebAuthReady()) {
                if (previous?.localEarlyCheckIn == true) earlyCheckInBeaconIds += id
                restoreEngagementSnapshot(id, previous)
                _beaconCheckInPendingIds.update { it - id }
                return@launch
            }
            mapBeaconRepository.checkOutBeacon(id).fold(
                onSuccess = {
                    _beaconCheckInPendingIds.update { it - id }
                    invalidateBeaconAttendeeDirectory(id)
                    if (_beaconRsvpById.value[id]?.currentUserSignedUp == true) {
                        loadBeaconAttendeeDirectory(id, forceRefresh = true)
                    }
                },
                onFailure = {
                    if (previous?.localEarlyCheckIn == true) earlyCheckInBeaconIds += id
                    restoreEngagementSnapshot(id, previous)
                    _beaconCheckInPendingIds.update { it - id }
                    _engagementSnackbar.value = "Couldn't undo check-in"
                },
            )
        }
        return
    }

    // Optimistic UI only — do not persist until the server confirms (or in-geofence early 409).
    // Persisting mid-flight caused "checked in" to survive app kill after a 403 far-away reject.
    _beaconCheckInPendingIds.update { it + id }
    updateBeaconEngagementCache(persistDisk = false) { current ->
        val base = current[id] ?: BeaconEngagementCacheEntry()
        current + (id to base.copy(checkedIn = true, localEarlyCheckIn = false))
    }
    PlatformHapticsPolicy.successNotification()
    viewModelScope.launch {
        if (!locationService.hasLocationPermission()) {
            restoreEngagementSnapshot(id, previous)
            _beaconCheckInPendingIds.update { it - id }
            _engagementSnackbar.value = "Location access is required to check in"
            return@launch
        }
        val loc = resolveBeaconDropLocation()
        if (loc == null ||
            !loc.latitude.isFinite() ||
            !loc.longitude.isFinite() ||
            (loc.latitude == 0.0 && loc.longitude == 0.0)
        ) {
            restoreEngagementSnapshot(id, previous)
            _beaconCheckInPendingIds.update { it - id }
            _engagementSnackbar.value = "Location required to check in"
            return@launch
        }
        val beacon =
            _mapBeacons.value.firstOrNull { it.id == id }
                ?: (_selection.value as? MapSelection.BeaconSelected)?.beacon?.takeIf { it.id == id }
        if (beacon != null) {
            val radiusM = beacon.resolveEventCheckInRadiusMeters()
            val distanceM =
                haversineDistance(
                    loc.latitude,
                    loc.longitude,
                    beacon.latitude,
                    beacon.longitude,
                )
            if (distanceM > radiusM) {
                restoreEngagementSnapshot(id, previous)
                _beaconCheckInPendingIds.update { it - id }
                _engagementSnackbar.value = "You're too far to check in"
                return@launch
            }
        }
        if (!ensureClickWebAuthReady()) {
            restoreEngagementSnapshot(id, previous)
            _beaconCheckInPendingIds.update { it - id }
            _engagementSnackbar.value = "Couldn't check in — try again"
            return@launch
        }
        mapBeaconRepository
            .checkInBeacon(
                id,
                engagementTelemetry(latitude = loc.latitude, longitude = loc.longitude),
            ).fold(
                onSuccess = { payload ->
                    if (payload.checkedIn) {
                        earlyCheckInBeaconIds -= id
                    }
                    updateBeaconEngagementCache { current ->
                        current + (
                            id to
                                BeaconEngagementCacheEntry(
                                    bookmarked = current[id]?.bookmarked ?: false,
                                    checkedIn = payload.checkedIn,
                                    checkedInAt = payload.checkedInAt,
                                    checkInCount = payload.checkInCount,
                                    localEarlyCheckIn = false,
                                    hubId =
                                        payload.hubId?.trim()?.takeIf { it.isNotEmpty() }
                                            ?: current[id]?.hubId,
                                )
                        )
                    }
                    _beaconCheckInPendingIds.update { it - id }
                    payload.hubId
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { applyBeaconHubId(id, it) }
                    invalidateBeaconAttendeeDirectory(id)
                    if (payload.checkedIn || _beaconRsvpById.value[id]?.currentUserSignedUp == true) {
                        loadBeaconAttendeeDirectory(id, forceRefresh = true)
                    }
                    _engagementSnackbar.value =
                        if (payload.checkedIn) {
                            "Checked in"
                        } else {
                            "Checked out"
                        }
                },
                onFailure = { err ->
                    val http = err as? BeaconEngagementHttpException
                    // Early check-in (409) is only valid when already inside the geofence —
                    // server now enforces this; still refuse to persist remote false positives.
                    if (http?.status == 409 && beacon != null) {
                        val radiusM = beacon.resolveEventCheckInRadiusMeters()
                        val distanceM =
                            haversineDistance(
                                loc.latitude,
                                loc.longitude,
                                beacon.latitude,
                                beacon.longitude,
                            )
                        if (distanceM <= radiusM) {
                            earlyCheckInBeaconIds += id
                            updateBeaconEngagementCache { current ->
                                val base = current[id] ?: BeaconEngagementCacheEntry()
                                current + (
                                    id to
                                        base.copy(
                                            checkedIn = true,
                                            localEarlyCheckIn = true,
                                        )
                                )
                            }
                            _beaconCheckInPendingIds.update { it - id }
                            invalidateBeaconAttendeeDirectory(id)
                            _engagementSnackbar.value = "Checked in early — see you at the event"
                            return@fold
                        }
                    }
                    restoreEngagementSnapshot(id, previous)
                    _beaconCheckInPendingIds.update { it - id }
                    _engagementSnackbar.value =
                        beaconCheckInFailureMessage(
                            httpStatus = http?.status,
                            fallback = http?.message,
                        )
                },
            )
    }
}

internal fun MapViewModel.restoreEngagementSnapshot(
    beaconId: String,
    previous: BeaconEngagementCacheEntry?,
) {
    updateBeaconEngagementCache { current ->
        if (previous == null) {
            current - beaconId
        } else {
            current + (beaconId to previous)
        }
    }
}

internal fun MapViewModel.rsvpToBeaconImpl(
    beaconId: String,
    onFinished: (Boolean) -> Unit = {},
) {
    val id = beaconId.trim()
    if (id.isEmpty() || id in _beaconRsvpPendingIds.value) return
    val previous = _beaconRsvpById.value[id]
    _beaconRsvpPendingIds.update { it + id }
    applyOptimisticRsvp(id, signedUp = true)
    PlatformHapticsPolicy.successNotification()
    viewModelScope.launch {
        if (!ensureClickWebAuthReady()) {
            restoreRsvpSnapshot(id, previous)
            _beaconRsvpPendingIds.update { it - id }
            _engagementSnackbar.value = "Sign-in still loading — try RSVP again"
            onFinished(false)
            return@launch
        }
        val cachedLoc = AppDataManager.lastKnownDeviceLocation.value
        mapBeaconRepository
            .rsvpBeacon(
                beaconId = id,
                latitude = cachedLoc?.first,
                longitude = cachedLoc?.second,
            ).fold(
                onSuccess = { attendee ->
                    updateBeaconRsvpCache { current ->
                        val prev = current[id]
                        val localAttendee = currentUserAsAttendee()
                        val confirmedAttendee =
                            attendee.copy(
                                name = attendee.name.takeIf { it.isNotBlank() } ?: localAttendee?.name ?: "You",
                                avatarUrl = localAttendee?.avatarUrl ?: attendee.avatarUrl,
                            )
                        val mergedAttendees =
                            (
                                (prev?.attendees.orEmpty())
                                    .filterNot { it.userId == confirmedAttendee.userId } + confirmedAttendee
                            ).distinctBy { it.userId }
                        current + (
                            id to
                                BeaconRsvpCacheEntry(
                                    attendees = mergedAttendees,
                                    currentUserSignedUp = true,
                                )
                        )
                    }
                    _beaconRsvpPendingIds.update { it - id }
                    loadBeaconAttendeeDirectory(id, forceRefresh = true)
                    onFinished(true)
                },
                onFailure = { e ->
                    restoreRsvpSnapshot(id, previous)
                    _beaconRsvpPendingIds.update { it - id }
                    _engagementSnackbar.value =
                        normalizeEventRsvpErrorMessage(e.message?.takeIf { it.isNotBlank() })
                            ?: "Could not update RSVP. Please try again."
                    onFinished(false)
                },
            )
    }
}

/** Cancels the current user's RSVP and removes them from the cached attendee list. */
internal fun MapViewModel.cancelRsvpToBeaconImpl(
    beaconId: String,
    onFinished: (Boolean) -> Unit = {},
) {
    val id = beaconId.trim()
    if (id.isEmpty() || id in _beaconRsvpPendingIds.value) return
    val previous = _beaconRsvpById.value[id]
    _beaconRsvpPendingIds.update { it + id }
    applyOptimisticRsvp(id, signedUp = false)
    PlatformHapticsPolicy.successNotification()
    viewModelScope.launch {
        if (!ensureClickWebAuthReady()) {
            restoreRsvpSnapshot(id, previous)
            _beaconRsvpPendingIds.update { it - id }
            _engagementSnackbar.value = "Sign-in still loading — try RSVP again"
            onFinished(false)
            return@launch
        }
        val currentUserId = AppDataManager.currentUser.value?.id
        mapBeaconRepository.cancelRsvp(id).fold(
            onSuccess = {
                updateBeaconRsvpCache { current ->
                    val prev = current[id]
                    val remaining =
                        prev
                            ?.attendees
                            .orEmpty()
                            .filterNot { it.userId == currentUserId }
                    current + (
                        id to
                            BeaconRsvpCacheEntry(
                                attendees = remaining,
                                currentUserSignedUp = false,
                            )
                    )
                }
                _beaconRsvpPendingIds.update { it - id }
                invalidateBeaconAttendeeDirectory(id)
                if (_beaconEngagementById.value[id]?.checkedIn == true) {
                    loadBeaconAttendeeDirectory(id, forceRefresh = true)
                }
                onFinished(true)
            },
            onFailure = { e ->
                restoreRsvpSnapshot(id, previous)
                _beaconRsvpPendingIds.update { it - id }
                _engagementSnackbar.value =
                    normalizeEventRsvpErrorMessage(e.message?.takeIf { it.isNotBlank() })
                        ?: "Could not update RSVP. Please try again."
                onFinished(false)
            },
        )
    }
}
