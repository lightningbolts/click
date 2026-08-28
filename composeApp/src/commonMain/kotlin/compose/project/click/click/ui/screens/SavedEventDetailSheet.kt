@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.api.EventBookmarkItemDto // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconMetadata // pragma: allowlist secret
import compose.project.click.click.data.models.parseEpochMs // pragma: allowlist secret
import compose.project.click.click.data.models.withPreservedEventScheduleFrom // pragma: allowlist secret
import compose.project.click.click.events.EventReminderCoordinator // pragma: allowlist secret
import compose.project.click.click.ui.components.BeaconShareToChatDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDefaults // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDialogChrome // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedToastHost // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberUnifiedToastState // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.ui.sheet.MapBeaconSheetRoot // pragma: allowlist secret
import compose.project.click.click.ui.utils.hasUsableMapCoordinates // pragma: allowlist secret
import compose.project.click.click.ui.utils.haversineDistance // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapViewModel // pragma: allowlist secret
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The same event-detail bottom sheet Home uses for a saved-event tap. Settings reuses this
 * instead of a second implementation that can drift (or a no-op click handler).
 */
@Composable
fun SavedEventDetailSheet(
    beacon: MapBeacon?,
    mapViewModel: MapViewModel,
    currentUserId: String?,
    onDismiss: () -> Unit,
    onShareBeaconToChats: ((MapBeacon, List<String>, String?) -> Unit)? = null,
) {
    val detailBeaconSeed = beacon ?: return
    val mapBeacons by mapViewModel.mapBeacons.collectAsState()
    val prefetchedBeacons by AppDataManager.prefetchedMapBeacons.collectAsState()
    val toastState = rememberUnifiedToastState()
    var shareSavedBeaconToChat by remember(detailBeaconSeed.id) { mutableStateOf<MapBeacon?>(null) }
    val detailBeacon =
        remember(detailBeaconSeed, mapBeacons, prefetchedBeacons) {
            val live =
                mapBeacons.firstOrNull { it.id == detailBeaconSeed.id }
                    ?: prefetchedBeacons.firstOrNull { it.id == detailBeaconSeed.id }
            live?.withPreservedEventScheduleFrom(detailBeaconSeed) ?: detailBeaconSeed
        }
    LaunchedEffect(detailBeaconSeed.id) {
        mapViewModel.ensureEventBeaconDetail(detailBeaconSeed.id, seed = detailBeaconSeed)
    }
    val detailSurface = GlassSheetTokens.OledBlack()
    val onDetailSurface = GlassSheetTokens.OnOled()
    val distanceMeters =
        AppDataManager.lastKnownDeviceLocation.value?.let { (lat, lon) ->
            haversineDistance(lat, lon, detailBeacon.latitude, detailBeacon.longitude)
        }
    val isCreator =
        !currentUserId.isNullOrBlank() &&
            detailBeacon.createdByUserId == currentUserId
    val inboxChats by AppDataManager.inboxFeedChats.collectAsState()
    MapBeaconSheetRoot(
        visible = true,
        onDismissRequest = {
            shareSavedBeaconToChat = null
            onDismiss()
        },
        containerColor = detailSurface,
        contentColor = onDetailSurface,
        scrimColor = Color.Black.copy(alpha = ClickSheetDefaults.ScrimAlpha),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        appColorScheme = MaterialTheme.colorScheme,
        appTypography = MaterialTheme.typography,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ClickSheetDialogChrome(
                modifier = Modifier.fillMaxWidth(),
                sheetColor = detailSurface,
                onSurface = onDetailSurface,
                alignSemanticColorsToSheet = true,
            ) {
                EventBeaconDetail(
                    beacon = detailBeacon,
                    distanceMeters = distanceMeters,
                    viewModel = mapViewModel,
                    isCreator = isCreator,
                    onEdit = onDismiss,
                    onDelete = onDismiss,
                    onShareToChat = {
                        shareSavedBeaconToChat = detailBeacon
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .sheetBodyScroll()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            UnifiedToastHost(
                state = toastState,
                opaque = true,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp)
                        .zIndex(100f),
            )
        }
        shareSavedBeaconToChat?.let { beaconToShare ->
            BeaconShareToChatDialog(
                beacon = beaconToShare,
                chats = inboxChats,
                onDismissRequest = { shareSavedBeaconToChat = null },
                onShare = { selectedChatIds, openChatConnectionId ->
                    onShareBeaconToChats?.invoke(
                        beaconToShare,
                        selectedChatIds,
                        openChatConnectionId,
                    )
                    shareSavedBeaconToChat = null
                },
            )
        }
    }
}

internal fun resolveSavedEventBeacon(
    bookmark: EventBookmarkItemDto,
    mapBeacons: List<MapBeacon>,
    prefetchedBeacons: List<MapBeacon>,
): MapBeacon {
    val id = bookmark.beaconId.trim()
    val base =
        mapBeacons.firstOrNull { it.id == id }
            ?: EventReminderCoordinator.beaconById(id)
            ?: prefetchedBeacons.firstOrNull { it.id == id }
            ?: return bookmark.toSyntheticMapBeacon()
    return base.withBookmarkScheduleOverlay(bookmark)
}

private fun MapBeacon.withBookmarkScheduleOverlay(bookmark: EventBookmarkItemDto): MapBeacon {
    val startIso = bookmark.eventStartAt?.trim()?.takeIf { it.isNotEmpty() }
    val endIso = bookmark.eventEndAt?.trim()?.takeIf { it.isNotEmpty() }
    val locationName = bookmark.locationName?.trim()?.takeIf { it.isNotEmpty() }
    val formattedAddress = bookmark.formattedAddress?.trim()?.takeIf { it.isNotEmpty() }
    val categories =
        bookmark.eventCategories
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    val bookmarkLat = bookmark.latitude
    val bookmarkLon = bookmark.longitude
    val adoptBookmarkCoords =
        !hasUsableMapCoordinates() &&
            bookmarkLat != null &&
            bookmarkLon != null &&
            bookmarkLat.isFinite() &&
            bookmarkLon.isFinite() &&
            !(bookmarkLat == 0.0 && bookmarkLon == 0.0)

    val existing = metadata.raw
    val merged =
        buildJsonObject {
            existing?.forEach { (k, v) -> put(k, v) }
            startIso?.let { put("event_start_at", JsonPrimitive(it)) }
            endIso?.let { put("event_end_at", JsonPrimitive(it)) }
            bookmark.title?.trim()?.takeIf { it.isNotEmpty() }?.let { title ->
                if (existing?.get("title") == null) put("title", JsonPrimitive(title))
            }
            if (metadata.locationName.isNullOrBlank() && locationName != null) {
                put("location_name", JsonPrimitive(locationName))
            }
            if (metadata.formattedAddress.isNullOrBlank() && formattedAddress != null) {
                put("formatted_address", JsonPrimitive(formattedAddress))
            }
            if (metadata.eventCategories.isEmpty() && categories.isNotEmpty()) {
                put("event_categories", JsonArray(categories.map { JsonPrimitive(it) }))
            }
        }
    return copy(
        latitude = if (adoptBookmarkCoords) bookmarkLat!! else latitude,
        longitude = if (adoptBookmarkCoords) bookmarkLon!! else longitude,
        createdByUserId = createdByUserId ?: bookmark.creatorId?.trim()?.takeIf { it.isNotEmpty() },
        creatorDisplayName = creatorDisplayName ?: bookmark.creatorName?.trim()?.takeIf { it.isNotEmpty() },
        createdAtEpochMs =
            createdAtEpochMs ?: bookmark.createdAt
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { parseEpochMs(it) },
        showCreatorName = showCreatorName || bookmark.showCreatorName,
        metadata =
            metadata.copy(
                title = metadata.title ?: bookmark.title?.trim()?.takeIf { it.isNotEmpty() },
                locationName = metadata.locationName?.takeIf { it.isNotBlank() } ?: locationName,
                formattedAddress = metadata.formattedAddress?.takeIf { it.isNotBlank() } ?: formattedAddress,
                eventCategories = metadata.eventCategories.ifEmpty { categories },
                raw = merged,
            ),
    )
}

internal fun EventBookmarkItemDto.toSyntheticMapBeacon(): MapBeacon {
    val lat = latitude ?: 0.0
    val lon = longitude ?: 0.0
    val title = this.title?.takeIf { it.isNotBlank() }
    val locationName = this.locationName?.takeIf { it.isNotBlank() }
    val formattedAddress = this.formattedAddress?.takeIf { it.isNotBlank() }
    val categories = eventCategories.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val raw =
        buildJsonObject {
            title?.let { put("title", JsonPrimitive(it)) }
            eventStartAt?.takeIf { it.isNotBlank() }?.let { put("event_start_at", JsonPrimitive(it)) }
            eventEndAt?.takeIf { it.isNotBlank() }?.let { put("event_end_at", JsonPrimitive(it)) }
            locationName?.let { put("location_name", JsonPrimitive(locationName)) }
            formattedAddress?.let { put("formatted_address", JsonPrimitive(formattedAddress)) }
            if (categories.isNotEmpty()) {
                put("event_categories", JsonArray(categories.map { JsonPrimitive(it) }))
            }
        }
    val expiresMs =
        expiresAt
            ?.let {
                compose.project.click.click.data.models // pragma: allowlist secret
                    .parseEpochMs(it)
            }
    return MapBeacon(
        id = beaconId,
        kind = MapBeaconKind.EVENT,
        latitude = lat,
        longitude = lon,
        metadata =
            MapBeaconMetadata(
                title = title,
                locationName = locationName,
                formattedAddress = formattedAddress,
                eventCategories = categories,
                raw = raw,
            ),
        createdByUserId = creatorId?.trim()?.takeIf { it.isNotEmpty() },
        createdAtEpochMs = createdAt?.trim()?.takeIf { it.isNotEmpty() }?.let { parseEpochMs(it) },
        expiresAtEpochMs = expiresMs,
        sourceBeaconType = "event",
        showCreatorName = showCreatorName,
        creatorDisplayName = creatorName?.trim()?.takeIf { it.isNotEmpty() },
    )
}
