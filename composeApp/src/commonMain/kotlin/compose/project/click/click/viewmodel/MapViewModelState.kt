@file:Suppress(
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.viewmodel // pragma: allowlist secret

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.collaboration.CollaborationSessionManager // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.ClickWebAuthCoordinator // pragma: allowlist secret
import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.BeaconAttendeeDto // pragma: allowlist secret
import compose.project.click.click.data.api.BeaconEngagementHttpException // pragma: allowlist secret
import compose.project.click.click.data.api.EngagementTelemetryBody // pragma: allowlist secret
import compose.project.click.click.data.api.MapBeaconPatchBody // pragma: allowlist secret
import compose.project.click.click.data.models.BeaconVisibilityAudience // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconInsert // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.parseEpochMs // pragma: allowlist secret
import compose.project.click.click.data.models.parseMapBeaconMetadata // pragma: allowlist secret
import compose.project.click.click.data.models.visibleMapConnections // pragma: allowlist secret
import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.ChatRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.MapBeaconRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseChatRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.BeaconEngagementPersistence // pragma: allowlist secret
import compose.project.click.click.data.storage.BeaconRsvpPersistence // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.events.EVENT_CATEGORIES_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.events.EVENT_CATEGORY_OPTIONS // pragma: allowlist secret
import compose.project.click.click.events.EVENT_CHECK_IN_RADIUS_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.events.EVENT_VENUE_SCALE_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.events.EventReminderCoordinator // pragma: allowlist secret
import compose.project.click.click.events.EventSchedule // pragma: allowlist secret
import compose.project.click.click.events.EventVenueScale // pragma: allowlist secret
import compose.project.click.click.events.beaconCheckInFailureMessage // pragma: allowlist secret
import compose.project.click.click.events.eventSchedule // pragma: allowlist secret
import compose.project.click.click.events.eventScheduleMetadata // pragma: allowlist secret
import compose.project.click.click.events.isVisibleEventBeacon // pragma: allowlist secret
import compose.project.click.click.events.mergeEventScheduleIntoRaw // pragma: allowlist secret
import compose.project.click.click.events.resolveEventCheckInRadiusMeters // pragma: allowlist secret
import compose.project.click.click.events.validateEventSchedule // pragma: allowlist secret
import compose.project.click.click.getPlatform // pragma: allowlist secret
import compose.project.click.click.ui.components.MapPin // pragma: allowlist secret
import compose.project.click.click.ui.components.MapPinKind // pragma: allowlist secret
import compose.project.click.click.ui.components.mapBeaconKindToLayerFilter // pragma: allowlist secret
import compose.project.click.click.ui.utils.BoundingBox // pragma: allowlist secret
import compose.project.click.click.ui.utils.CommunityHubPin // pragma: allowlist secret
import compose.project.click.click.ui.utils.ConnectionMapPoint // pragma: allowlist secret
import compose.project.click.click.ui.utils.MapCluster // pragma: allowlist secret
import compose.project.click.click.ui.utils.MapRenderData // pragma: allowlist secret
import compose.project.click.click.ui.utils.TimeState // pragma: allowlist secret
import compose.project.click.click.ui.utils.calculateZoomForBounds // pragma: allowlist secret
import compose.project.click.click.ui.utils.determineMapRenderData // pragma: allowlist secret
import compose.project.click.click.ui.utils.displayDynamicTitle // pragma: allowlist secret
import compose.project.click.click.ui.utils.hasUsableMapCoordinates // pragma: allowlist secret
import compose.project.click.click.ui.utils.haversineDistance // pragma: allowlist secret
import compose.project.click.click.ui.utils.mapPeerDisplayNameForPin // pragma: allowlist secret
import compose.project.click.click.ui.utils.mergeCommunityHubLists // pragma: allowlist secret
import compose.project.click.click.ui.utils.mergeMapBeaconLists // pragma: allowlist secret
import compose.project.click.click.ui.utils.overlappingMapPins // pragma: allowlist secret
import compose.project.click.click.ui.utils.resolveBeaconQuickDistanceMeters // pragma: allowlist secret
import compose.project.click.click.ui.utils.toMapPoint // pragma: allowlist secret
import compose.project.click.click.util.compressOutgoingChatImageForUpload // pragma: allowlist secret
import compose.project.click.click.util.isValidStreamingUrl // pragma: allowlist secret
import compose.project.click.click.util.teardownBlocking // pragma: allowlist secret
import compose.project.click.click.utils.EVENT_FORMATTED_ADDRESS_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.utils.EVENT_LOCATION_NAME_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.utils.GeocodedPlace // pragma: allowlist secret
import compose.project.click.click.utils.HUB_GATEKEEPER_HIGH_ACCURACY_TIMEOUT_MS // pragma: allowlist secret
import compose.project.click.click.utils.LocationResult // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import compose.project.click.click.utils.resolveHubGatekeeperLocation // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject // pragma: allowlist secret
import kotlinx.serialization.json.JsonPrimitive // pragma: allowlist secret
import kotlinx.serialization.json.add // pragma: allowlist secret
import kotlinx.serialization.json.buildJsonObject // pragma: allowlist secret
import kotlinx.serialization.json.put // pragma: allowlist secret
import kotlinx.serialization.json.putJsonArray // pragma: allowlist secret
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * State representing the map loading/error status
 */
sealed class MapState {
    object Loading : MapState()

    data class Success(
        val connections: List<Connection>,
    ) : MapState()

    data class Error(
        val message: String,
    ) : MapState()
}

/**
 * Represents the selected item on the map (either a cluster or individual connection)
 */
sealed class MapSelection {
    object None : MapSelection()

    data class ClusterSelected(
        val cluster: MapCluster,
    ) : MapSelection()

    data class ConnectionSelected(
        val point: ConnectionMapPoint,
        val otherUser: User?,
    ) : MapSelection()

    data class BeaconSelected(
        val beacon: MapBeacon,
        val distanceMeters: Double?,
    ) : MapSelection()

    data class HubSelected(
        val hub: CommunityHubPin,
        val distanceMeters: Double?,
        /** `null` while proximity is still being resolved. */
        val canJoinGeofence: Boolean?,
    ) : MapSelection()

    /**
     * Two or more pins share nearly the same on-screen hit target. User picks which one to open.
     */
    data class OverlappingPinsSelected(
        val pins: List<MapPin>,
    ) : MapSelection()
}

data class BeaconRsvpCacheEntry(
    val attendees: List<BeaconAttendeeDto>,
    val currentUserSignedUp: Boolean,
)

data class BeaconDirectoryCacheEntry(
    val attendees: List<compose.project.click.click.events.DirectoryAttendee>,
    val currentUserSignedUp: Boolean,
    val currentUserCheckedIn: Boolean,
    val mutualsSectionUnlocked: Boolean,
)

data class BeaconEngagementCacheEntry(
    val bookmarked: Boolean = false,
    val checkedIn: Boolean = false,
    val checkedInAt: String? = null,
    val checkInCount: Int = 0,
    /**
     * Client-kept early check-in after HTTP 409 (event not live yet). Survives force-refresh
     * and process death until the server reports checkedIn or the user checks out.
     */
    val localEarlyCheckIn: Boolean = false,
)

/**
 * Camera target for map animations
 */
data class CameraTarget(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
)

/**
 * Map statistics
 */
data class MapStats(
    val totalConnections: Int,
    val liveCount: Int,
    val recentCount: Int,
    val archiveCount: Int,
)

/**
 * Helper for combining 4 flows
 */
internal data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

private data class Sextuple<A, B, C, D, E, F>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F,
)

private data class Septuple<A, B, C, D, E, F, G>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F,
    val seventh: G,
)

private data class Octuple<A, B, C, D, E, F, G, H>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F,
    val seventh: G,
    val eighth: H,
)

internal data class Nonuple<A, B, C, D, E, F, G, H, I>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F,
    val seventh: G,
    val eighth: H,
    val ninth: I,
)

private const val DISCOVERY_PREFETCH_DEBOUNCE_MS = 400L
