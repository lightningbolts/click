@file:Suppress(
    "ktlint:standard:no-consecutive-comments",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.viewmodel // pragma: allowlist secret

import androidx.lifecycle.viewModelScope
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.collaboration.CollaborationSessionManager // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.api.MapBeaconPatchBody // pragma: allowlist secret
import compose.project.click.click.data.models.BeaconVisibilityAudience // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconInsert // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.models.parseMapBeaconMetadata // pragma: allowlist secret
import compose.project.click.click.events.EVENT_CATEGORIES_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.events.EVENT_CATEGORY_OPTIONS // pragma: allowlist secret
import compose.project.click.click.events.EVENT_CHECK_IN_RADIUS_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.events.EVENT_VENUE_SCALE_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.events.EventListingOptions // pragma: allowlist secret
import compose.project.click.click.events.EventReminderCoordinator // pragma: allowlist secret
import compose.project.click.click.events.EventSchedule // pragma: allowlist secret
import compose.project.click.click.events.EventVenueScale // pragma: allowlist secret
import compose.project.click.click.events.eventSchedule // pragma: allowlist secret
import compose.project.click.click.events.eventScheduleMetadata // pragma: allowlist secret
import compose.project.click.click.events.mergeEventScheduleIntoRaw // pragma: allowlist secret
import compose.project.click.click.events.toMetadataPatch // pragma: allowlist secret
import compose.project.click.click.events.validateEventSchedule // pragma: allowlist secret
import compose.project.click.click.ui.components.mapBeaconKindToLayerFilter // pragma: allowlist secret
import compose.project.click.click.ui.utils.hasUsableMapCoordinates // pragma: allowlist secret
import compose.project.click.click.ui.utils.haversineDistance // pragma: allowlist secret
import compose.project.click.click.ui.utils.mergeMapBeaconLists // pragma: allowlist secret
import compose.project.click.click.ui.utils.resolveBeaconQuickDistanceMeters // pragma: allowlist secret
import compose.project.click.click.util.compressOutgoingChatImageForUpload // pragma: allowlist secret
import compose.project.click.click.util.isValidStreamingUrl // pragma: allowlist secret
import compose.project.click.click.utils.EVENT_FORMATTED_ADDRESS_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.utils.EVENT_LOCATION_NAME_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.utils.GeocodedPlace // pragma: allowlist secret
import compose.project.click.click.utils.LocationResult // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject // pragma: allowlist secret
import kotlinx.serialization.json.JsonPrimitive // pragma: allowlist secret
import kotlinx.serialization.json.add // pragma: allowlist secret
import kotlinx.serialization.json.buildJsonObject // pragma: allowlist secret
import kotlinx.serialization.json.put // pragma: allowlist secret
import kotlinx.serialization.json.putJsonArray // pragma: allowlist secret
import kotlin.random.Random

internal fun MapViewModel.onBeaconPinTappedImpl(
    beaconId: String,
    seedDistanceMeters: Double? = null,
) {
    val beacon =
        _mapBeacons.value.firstOrNull { it.id == beaconId }
            ?: return
    val quickDistance =
        resolveBeaconQuickDistanceMeters(
            seedDistanceMeters = seedDistanceMeters,
            beaconLat = beacon.latitude,
            beaconLon = beacon.longitude,
            cachedUserLatLon = AppDataManager.lastKnownDeviceLocation.value,
        )
    _selection.value = MapSelection.BeaconSelected(beacon, distanceMeters = quickDistance)

    viewModelScope.launch(Dispatchers.Default) {
        val loc = resolveFastMapLocation() ?: return@launch
        val distance = haversineDistance(loc.latitude, loc.longitude, beacon.latitude, beacon.longitude)
        val current = _selection.value as? MapSelection.BeaconSelected ?: return@launch
        if (current.beacon.id == beaconId) {
            _selection.value = current.copy(distanceMeters = distance)
        }
    }
}

/**
 * Detail-sheet only: fetch the full beacon when schedule, Posted (`created_at`), or creator
 * attribution is missing — and at least once per id when the sheet opens, so bookmark /
 * proximity stubs that already have a schedule still pick up Host + Posted.
 *
 * [seed] is required when opening from Home with a synthetic/bookmark beacon that is not yet
 * in [_mapBeacons] or [MapSelection].
 */
internal fun MapViewModel.ensureEventBeaconDetailImpl(
    beaconId: String,
    seed: MapBeacon? = null,
) {
    val id = beaconId.trim()
    if (id.isEmpty()) return
    val current =
        _mapBeacons.value.firstOrNull { it.id == id }
            ?: (_selection.value as? MapSelection.BeaconSelected)?.beacon?.takeIf { it.id == id }
            ?: seed?.takeIf { it.id == id && it.kind == MapBeaconKind.EVENT }
            ?: return
    if (current.kind != MapBeaconKind.EVENT) return
    val needsSchedule = current.eventSchedule() == null
    val needsPosted = current.createdAtEpochMs == null
    val needsCreator = current.createdByUserId.isNullOrBlank()
    val needsHostName = current.creatorDisplayName.isNullOrBlank()
    val needsVenueLabel =
        current.hasUsableMapCoordinates() &&
            current.metadata.locationName.isNullOrBlank() &&
            current.metadata.formattedAddress.isNullOrBlank()
    val needsHub = current.kind == MapBeaconKind.EVENT && current.hubId.isNullOrBlank()
    val alreadyHydrated = id in eventDetailHydratedIds
    // Hydrate whenever any detail field is still missing — including host display name.
    if (
        alreadyHydrated &&
        !needsSchedule &&
        !needsPosted &&
        !needsCreator &&
        !needsHostName &&
        !needsVenueLabel &&
        !needsHub
    ) {
        return
    }
    viewModelScope.launch(Dispatchers.Default) {
        hydrateBeaconDetailFromNetwork(id, current)
    }
}

/**
 * Hydrates soundtrack preview/art/URLs for the detail sheet. Disk cache historically stripped
 * these fields, which left play controls missing until a fresh proximity fetch.
 */
internal fun MapViewModel.ensureSoundtrackBeaconDetailImpl(
    beaconId: String,
    seed: MapBeacon? = null,
) {
    val id = beaconId.trim()
    if (id.isEmpty()) return
    val current =
        _mapBeacons.value.firstOrNull { it.id == id }
            ?: (_selection.value as? MapSelection.BeaconSelected)?.beacon?.takeIf { it.id == id }
            ?: seed?.takeIf { it.id == id && it.kind == MapBeaconKind.SOUNDTRACK }
            ?: return
    if (current.kind != MapBeaconKind.SOUNDTRACK) return
    val needsPreview = current.metadata.previewUrl.isNullOrBlank()
    val needsArt = current.metadata.albumArtUrl.isNullOrBlank()
    val needsTrack = current.metadata.trackName.isNullOrBlank() && current.metadata.title.isNullOrBlank()
    val alreadyHydrated = id in soundtrackDetailHydratedIds
    if (alreadyHydrated && !needsPreview && !needsArt && !needsTrack) return
    viewModelScope.launch(Dispatchers.Default) {
        hydrateBeaconDetailFromNetwork(id, current)
    }
}

internal suspend fun MapViewModel.hydrateBeaconDetailFromNetwork(
    id: String,
    current: MapBeacon,
) {
    mapBeaconRepository.fetchBeacon(id).fold(
        onSuccess = { full ->
            fun MapBeacon.withHydratedDetail(): MapBeacon {
                val schedule = eventSchedule() ?: full.eventSchedule()
                val keepCoords = hasUsableMapCoordinates()

                fun String?.orHydrated(other: String?): String? = this?.takeIf { it.isNotBlank() } ?: other?.takeIf { it.isNotBlank() }
                val locationName = metadata.locationName.orHydrated(full.metadata.locationName)
                val formattedAddress =
                    metadata.formattedAddress.orHydrated(full.metadata.formattedAddress)
                val mergedRawBase =
                    buildJsonObject {
                        // Prefer server raw (venue keys) then local overlay keys.
                        full.metadata.raw?.forEach { (k, v) -> put(k, v) }
                        metadata.raw?.forEach { (k, v) -> put(k, v) }
                        locationName?.let { put("location_name", JsonPrimitive(it)) }
                        formattedAddress?.let { put("formatted_address", JsonPrimitive(it)) }
                    }
                return copy(
                    latitude = if (keepCoords) latitude else full.latitude,
                    longitude = if (keepCoords) longitude else full.longitude,
                    metadata =
                        metadata.copy(
                            title = metadata.title.orHydrated(full.metadata.title),
                            description = metadata.description.orHydrated(full.metadata.description),
                            trackName = metadata.trackName.orHydrated(full.metadata.trackName),
                            artistName = metadata.artistName.orHydrated(full.metadata.artistName),
                            artist = metadata.artist.orHydrated(full.metadata.artist),
                            previewUrl = metadata.previewUrl.orHydrated(full.metadata.previewUrl),
                            albumArtUrl = metadata.albumArtUrl.orHydrated(full.metadata.albumArtUrl),
                            musicUrl = metadata.musicUrl.orHydrated(full.metadata.musicUrl),
                            originalUrl = metadata.originalUrl.orHydrated(full.metadata.originalUrl),
                            locationName = locationName,
                            formattedAddress = formattedAddress,
                            eventCategories =
                                metadata.eventCategories.ifEmpty {
                                    full.metadata.eventCategories
                                },
                            raw =
                                if (schedule != null) {
                                    mergeEventScheduleIntoRaw(mergedRawBase, schedule)
                                } else {
                                    mergedRawBase
                                },
                        ),
                    createdByUserId = createdByUserId ?: full.createdByUserId,
                    createdAtEpochMs = createdAtEpochMs ?: full.createdAtEpochMs,
                    expiresAtEpochMs = expiresAtEpochMs ?: full.expiresAtEpochMs,
                    showCreatorName = showCreatorName || full.showCreatorName,
                    creatorDisplayName = creatorDisplayName.orHydrated(full.creatorDisplayName),
                    sourceBeaconType = sourceBeaconType ?: full.sourceBeaconType,
                    hubId = hubId?.takeIf { it.isNotBlank() } ?: full.hubId,
                )
            }
            _mapBeacons.update { list ->
                var found = false
                val mapped =
                    list.map { b ->
                        if (b.id == id) {
                            found = true
                            b.withHydratedDetail()
                        } else {
                            b
                        }
                    }
                if (found) {
                    mapped
                } else {
                    mergeMapBeaconLists(list, listOf(current.withHydratedDetail()))
                }
            }
            val patched =
                _mapBeacons.value.firstOrNull { it.id == id }
                    ?: current.withHydratedDetail()
            when (patched.kind) {
                MapBeaconKind.EVENT -> eventDetailHydratedIds += id
                MapBeaconKind.SOUNDTRACK -> soundtrackDetailHydratedIds += id
                else -> Unit
            }
            AppDataManager.mergeCachedMapBeacons(listOf(patched))
            val sel = _selection.value as? MapSelection.BeaconSelected
            if (sel != null && sel.beacon.id == id) {
                _selection.value = sel.copy(beacon = patched)
            }
        },
        onFailure = { /* keep sheet open with whatever we have */ },
    )
}

internal fun MapViewModel.applyBeaconHubId(
    beaconId: String,
    hubId: String,
) {
    val id = beaconId.trim()
    val hid = hubId.trim()
    if (id.isEmpty() || hid.isEmpty()) return
    fun MapBeacon.withHub(): MapBeacon = if (this.hubId == hid) this else copy(hubId = hid)
    val base =
        _mapBeacons.value.firstOrNull { it.id == id }
            ?: (_selection.value as? MapSelection.BeaconSelected)?.beacon?.takeIf { it.id == id }
            ?: AppDataManager.prefetchedMapBeacons.value.firstOrNull { it.id == id }
            ?: return
    val patched = base.withHub()
    _mapBeacons.update { list ->
        if (list.none { it.id == id }) {
            list
        } else {
            list.map { if (it.id == id) patched else it }
        }
    }
    AppDataManager.mergeCachedMapBeacons(listOf(patched))
    val sel = _selection.value as? MapSelection.BeaconSelected
    if (sel != null && sel.beacon.id == id) {
        _selection.value = sel.copy(beacon = patched)
    }
}

/** @deprecated Use [ensureEventBeaconDetail]. */
internal fun MapViewModel.ensureEventBeaconScheduleImpl(beaconId: String) = ensureEventBeaconDetail(beaconId)

/**
 * Pan the camera to [beaconId] and open its detail sheet (Home Featured Event / deep link).
 * Ensures the matching layer for this beacon kind is visible so the pin isn't filtered out.
 */

/**
 * Pan the camera to [beaconId] and open its detail sheet (Home Featured Event / deep link).
 * Ensures the matching layer for this beacon kind is visible so the pin isn't filtered out.
 */
internal fun MapViewModel.focusBeaconOnMapImpl(
    beaconId: String,
    seedDistanceMeters: Double? = null,
) {
    val id = beaconId.trim()
    if (id.isEmpty()) return
    val beacon =
        _mapBeacons.value.firstOrNull { it.id == id }
            ?: EventReminderCoordinator.beaconById(id)
            ?: return
    if (_mapBeacons.value.none { it.id == id }) {
        _mapBeacons.update { current ->
            if (current.any { it.id == id }) current else current + beacon
        }
    }
    val neededLayer = mapBeaconKindToLayerFilter(beacon.kind)
    _selectedLayerFilters.update { filters ->
        if (MapLayerFilter.ALL in filters || neededLayer in filters) {
            filters
        } else {
            filters + neededLayer
        }
    }
    val zoom = 15.0
    val target =
        CameraTarget(
            latitude = beacon.latitude,
            longitude = beacon.longitude,
            zoom = zoom,
        )
    // Mark device-seed done so a late GPS fix cannot overwrite this focus.
    seededDeviceCameraThisSession = true
    _cameraTarget.value = target
    MapViewModel.lastKnownCameraTarget = target
    pendingProgrammaticZoomTarget = zoom
    pendingProgrammaticZoomSetAtMs = Clock.System.now().toEpochMilliseconds()
    _zoomLevel.value = zoom
    updateStickyPinModeForZoom(zoom)
    onBeaconPinTapped(id, seedDistanceMeters)
}

internal suspend fun MapViewModel.resolveBeaconDropLocation(): LocationResult? =
    locationService.getHighAccuracyLocation(4_500L)
        ?: locationService.getCurrentLocation()
        ?: AppDataManager.lastKnownDeviceLocation.value?.let { (lat, lon) ->
            LocationResult(latitude = lat, longitude = lon)
        }

internal fun MapViewModel.deleteOwnedBeaconImpl(
    beaconId: String,
    onFinished: (Boolean) -> Unit = {},
) {
    viewModelScope.launch {
        mapBeaconRepository.deleteBeacon(beaconId).fold(
            onSuccess = {
                _mapBeacons.update { list -> list.filterNot { it.id == beaconId } }
                updateBeaconRsvpCache { it - beaconId }
                updateBeaconEngagementCache { it - beaconId }
                if (_selection.value is MapSelection.BeaconSelected &&
                    (_selection.value as MapSelection.BeaconSelected).beacon.id == beaconId
                ) {
                    _selection.value = MapSelection.None
                }
                PlatformHapticsPolicy.successNotification()
                onFinished(true)
            },
            onFailure = {
                _beaconDropFailureToast.value = it.message ?: "Could not delete beacon"
                onFinished(false)
            },
        )
    }
}

internal fun MapViewModel.updateOwnedBeaconDescriptionImpl(
    beaconId: String,
    description: String,
    onFinished: (Boolean) -> Unit = {},
) {
    viewModelScope.launch {
        val patch =
            MapBeaconPatchBody(
                metadata = buildJsonObject { put("description", description.trim()) },
            )
        mapBeaconRepository.updateBeacon(beaconId, patch).fold(
            onSuccess = { updated ->
                _mapBeacons.update { list -> mergeMapBeaconLists(list, listOf(updated)) }
                val merged = _mapBeacons.value.firstOrNull { it.id == beaconId } ?: updated
                val sel = _selection.value
                if (sel is MapSelection.BeaconSelected && sel.beacon.id == beaconId) {
                    _selection.value = sel.copy(beacon = merged)
                }
                PlatformHapticsPolicy.successNotification()
                onFinished(true)
            },
            onFailure = {
                _beaconDropFailureToast.value = it.message ?: "Could not update beacon"
                onFinished(false)
            },
        )
    }
}

internal fun MapViewModel.submitBeaconDropImpl(
    kind: MapBeaconKind,
    title: String,
    description: String? = null,
    soundtrackUrl: String? = null,
    ttlMs: Long? = null,
    showCreatorName: Boolean = false,
    visibilityAudience: BeaconVisibilityAudience = BeaconVisibilityAudience.EVERYONE,
    eventSchedule: EventSchedule? = null,
    eventCategories: List<String> = emptyList(),
    venueScale: EventVenueScale = EventVenueScale.DEFAULT,
    eventLocation: GeocodedPlace? = null,
    eventListingOptions: EventListingOptions? = null,
    imageBytes: ByteArray? = null,
    imageMime: String? = null,
    onAcceptedLocally: () -> Unit = {},
    onRejectedEarly: () -> Unit = {},
    onRemoteFinished: (Boolean) -> Unit = {},
) {
    viewModelScope.launch {
        beaconSubmitMutex.lock()
        _beaconSubmitInFlight.value = true
        try {
            _beaconInsertError.value = null
            _beaconDropFailureToast.value = null
            val useProvidedEventLocation =
                kind == MapBeaconKind.EVENT &&
                    eventLocation != null &&
                    eventLocation.latitude.isFinite() &&
                    eventLocation.longitude.isFinite()
            if (!useProvidedEventLocation && !locationService.hasLocationPermission()) {
                _beaconInsertError.value =
                    "Location is required to drop a community beacon. Enable location in Settings and try again."
                onRejectedEarly()
                onRemoteFinished(false)
                return@launch
            }
            val locationDeferred =
                if (useProvidedEventLocation) {
                    null
                } else {
                    async(Dispatchers.Default) { resolveBeaconDropLocation() }
                }
            val trimmedTitle = title.trim()
            val trimmedDescription = description?.trim()?.takeIf { it.isNotEmpty() }
            val metadata: JsonObject? =
                when (kind) {
                    MapBeaconKind.SOUNDTRACK -> {
                        val url = soundtrackUrl?.trim().orEmpty()
                        if (!isValidStreamingUrl(url)) {
                            _beaconInsertError.value = "Enter a valid Spotify, Apple Music, or YouTube link."
                            onRejectedEarly()
                            onRemoteFinished(false)
                            return@launch
                        }
                        buildJsonObject {
                            put("music_url", url)
                        }
                    }
                    MapBeaconKind.EVENT -> {
                        if (trimmedTitle.isEmpty()) {
                            _beaconInsertError.value = "Please add a title."
                            onRejectedEarly()
                            onRemoteFinished(false)
                            return@launch
                        }
                        if (trimmedTitle.length > 80) {
                            _beaconInsertError.value = "Title must be 80 characters or less."
                            onRejectedEarly()
                            onRemoteFinished(false)
                            return@launch
                        }
                        if (trimmedDescription != null && trimmedDescription.length > 500) {
                            _beaconInsertError.value = "Description must be 500 characters or less."
                            onRejectedEarly()
                            onRemoteFinished(false)
                            return@launch
                        }
                        val schedule =
                            eventSchedule ?: run {
                                _beaconInsertError.value = "Pick event start and end times."
                                onRejectedEarly()
                                onRemoteFinished(false)
                                return@launch
                            }
                        validateEventSchedule(schedule.startEpochMs, schedule.endEpochMs)?.let { err ->
                            _beaconInsertError.value =
                                when (err) {
                                    compose.project.click.click.events.EventScheduleValidationError.EndBeforeStart ->
                                        "Event end must be after start."
                                    compose.project.click.click.events.EventScheduleValidationError.StartInPast ->
                                        "Event start must be in the future."
                                    compose.project.click.click.events.EventScheduleValidationError.DurationExceedsOneMonth ->
                                        "Events can last at most 1 month."
                                }
                            onRejectedEarly()
                            onRemoteFinished(false)
                            return@launch
                        }
                        if (!useProvidedEventLocation) {
                            _beaconInsertError.value =
                                "Set an event location (search an address or use my location)."
                            onRejectedEarly()
                            onRemoteFinished(false)
                            return@launch
                        }
                        val listingOptions = eventListingOptions ?: EventListingOptions()
                        buildJsonObject {
                            put("title", trimmedTitle)
                            trimmedDescription?.let { put("description", it) }
                            eventScheduleMetadata(schedule).forEach { (k, v) -> put(k, v) }
                            listingOptions.toMetadataPatch().forEach { (k, v) -> put(k, v) }
                            val categories =
                                eventCategories
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() && it in EVENT_CATEGORY_OPTIONS }
                                    .distinct()
                            if (categories.isNotEmpty()) {
                                putJsonArray(EVENT_CATEGORIES_METADATA_KEY) {
                                    categories.forEach { add(it) }
                                }
                            }
                            put(EVENT_VENUE_SCALE_METADATA_KEY, venueScale.apiValue)
                            put(EVENT_CHECK_IN_RADIUS_METADATA_KEY, venueScale.radiusMeters)
                            val locationName =
                                eventLocation!!.shortLabel.trim().ifEmpty {
                                    eventLocation.displayName.trim()
                                }
                            if (locationName.isNotEmpty()) {
                                put(EVENT_LOCATION_NAME_METADATA_KEY, locationName)
                            }
                            val formatted = eventLocation.displayName.trim()
                            if (formatted.isNotEmpty()) {
                                put(EVENT_FORMATTED_ADDRESS_METADATA_KEY, formatted)
                            }
                        }
                    }
                    MapBeaconKind.SOS, MapBeaconKind.HAZARD, MapBeaconKind.UTILITY, MapBeaconKind.STUDY -> {
                        if (trimmedTitle.isEmpty()) {
                            _beaconInsertError.value = "Please add a title."
                            onRejectedEarly()
                            onRemoteFinished(false)
                            return@launch
                        }
                        if (trimmedTitle.length > 80) {
                            _beaconInsertError.value = "Title must be 80 characters or less."
                            onRejectedEarly()
                            onRemoteFinished(false)
                            return@launch
                        }
                        if (trimmedDescription != null && trimmedDescription.length > 500) {
                            _beaconInsertError.value = "Description must be 500 characters or less."
                            onRejectedEarly()
                            onRemoteFinished(false)
                            return@launch
                        }
                        buildJsonObject {
                            put("title", trimmedTitle)
                            trimmedDescription?.let { put("description", it) }
                        }
                    }
                    else -> {
                        if (trimmedTitle.isEmpty()) {
                            _beaconInsertError.value = "Please add a title."
                            onRejectedEarly()
                            onRemoteFinished(false)
                            return@launch
                        }
                        if (trimmedTitle.length > 80) {
                            _beaconInsertError.value = "Title must be 80 characters or less."
                            onRejectedEarly()
                            onRemoteFinished(false)
                            return@launch
                        }
                        if (trimmedDescription != null && trimmedDescription.length > 500) {
                            _beaconInsertError.value = "Description must be 500 characters or less."
                            onRejectedEarly()
                            onRemoteFinished(false)
                            return@launch
                        }
                        buildJsonObject {
                            put("title", trimmedTitle)
                            trimmedDescription?.let { put("description", it) }
                        }
                    }
                }
            // A photo is optional for every beacon kind — it is encouraged in the drop sheet, not
            // gated. Beacons without one fall back to their generated gradient everywhere.
            val metadataWithImage: JsonObject? =
                run {
                    val raw = imageBytes
                    if (raw == null || raw.isEmpty()) {
                        return@run metadata
                    }
                    val mime = imageMime?.trim().orEmpty().ifEmpty { "image/jpeg" }
                    val compressed = compressOutgoingChatImageForUpload(raw, mime)
                    if (compressed.size > 2_000_000) {
                        _beaconInsertError.value = "Image must be under 2 MB after compression."
                        onRejectedEarly()
                        onRemoteFinished(false)
                        return@launch
                    }
                    val uploaded =
                        apiClient.uploadBeaconImage(
                            compressed,
                            if (compressed !== raw) "image/jpeg" else mime,
                        )
                    val url =
                        uploaded.getOrElse { err ->
                            _beaconInsertError.value =
                                err.message?.take(180) ?: "Could not upload beacon photo."
                            onRejectedEarly()
                            onRemoteFinished(false)
                            return@launch
                        }
                    buildJsonObject {
                        metadata?.forEach { (k, v) -> put(k, v) }
                        put("image_url", url)
                    }
                }
            val locLat: Double
            val locLon: Double
            if (useProvidedEventLocation) {
                locLat = eventLocation!!.latitude
                locLon = eventLocation.longitude
            } else {
                val loc =
                    locationDeferred!!.await()
                        ?: run {
                            _beaconInsertError.value =
                                "Could not read GPS. Enable location and try again."
                            onRejectedEarly()
                            onRemoteFinished(false)
                            return@launch
                        }
                locLat = loc.latitude
                locLon = loc.longitude
            }
            val squadSession = CollaborationSessionManager.activeMapDropSession()
            val eventExpiresIso =
                eventSchedule?.endEpochMs?.let {
                    kotlinx.datetime.Instant
                        .fromEpochMilliseconds(it)
                        .toString()
                }
            val listingForInsert =
                if (kind == MapBeaconKind.EVENT) {
                    eventListingOptions ?: EventListingOptions()
                } else {
                    null
                }
            val insert =
                MapBeaconInsert(
                    kind = kind.apiValue,
                    lat = locLat,
                    lon = locLon,
                    metadata = metadataWithImage,
                    ttlMs =
                        when {
                            kind == MapBeaconKind.SOUNDTRACK -> null
                            kind == MapBeaconKind.EVENT -> null
                            else -> ttlMs ?: (6L * 60L * 60_000L)
                        },
                    expiresAtIso = eventExpiresIso,
                    showCreatorName = showCreatorName,
                    visibilityAudience = visibilityAudience.apiValue,
                    eventVisibility = listingForInsert?.eventVisibility?.apiValue,
                    eventCapacity = listingForInsert?.eventCapacity,
                    approvalRequired = listingForInsert?.approvalRequired,
                    guestListVisibility = listingForInsert?.guestListVisibility?.apiValue,
                    coverThemeId = listingForInsert?.coverThemeId,
                    encounterId = squadSession?.encounterId,
                )
            val optimisticId = "optimistic:${Clock.System.now().toEpochMilliseconds()}:${Random.Default.nextInt()}"
            val optimisticBeacon =
                MapBeacon(
                    id = optimisticId,
                    kind = kind,
                    latitude = locLat,
                    longitude = locLon,
                    metadata = parseMapBeaconMetadata(metadataWithImage),
                    createdByUserId = AppDataManager.currentUser.value?.id,
                    createdAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                    expiresAtEpochMs = eventSchedule?.endEpochMs,
                    sourceBeaconType = insert.kind,
                    showCreatorName = showCreatorName,
                )
            if (!ensureClickWebAuthReady()) {
                _beaconInsertError.value = "Sign in again to drop beacons"
                onRejectedEarly()
                onRemoteFinished(false)
                return@launch
            }

            _mapBeacons.value = _mapBeacons.value + optimisticBeacon
            EventReminderCoordinator.rememberBeacon(optimisticBeacon)
            PlatformHapticsPolicy.heavyImpact()
            PlatformHapticsPolicy.successNotification()
            onAcceptedLocally()

            val insertResult = mapBeaconRepository.insertBeacon(insert)
            insertResult.fold(
                onSuccess = { serverBeacon ->
                    val confirmed =
                        if (
                            serverBeacon.latitude.isFinite() &&
                            serverBeacon.longitude.isFinite() &&
                            !(serverBeacon.latitude == 0.0 && serverBeacon.longitude == 0.0)
                        ) {
                            serverBeacon
                        } else {
                            // Insert response can lack parseable PostGIS location — keep drop coords.
                            serverBeacon.copy(latitude = locLat, longitude = locLon)
                        }
                    _mapBeacons.update { current ->
                        mergeMapBeaconLists(
                            current.filter { it.id != optimisticId },
                            listOf(confirmed),
                        )
                    }
                    EventReminderCoordinator.rememberBeacon(confirmed)
                    refreshBeaconsAfterDrop(
                        latitude = locLat,
                        longitude = locLon,
                        confirmedBeacon = confirmed,
                    )
                    onRemoteFinished(true)
                    PlatformHapticsPolicy.heavyImpact()
                    PlatformHapticsPolicy.successNotification()
                },
                onFailure = { e ->
                    _mapBeacons.value = _mapBeacons.value.filter { it.id != optimisticId }
                    _beaconDropFailureToast.value = e.message ?: "Could not drop beacon"
                    onRemoteFinished(false)
                },
            )
        } finally {
            _beaconSubmitInFlight.value = false
            beaconSubmitMutex.unlock()
        }
    }
}
