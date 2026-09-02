@file:Suppress(
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.viewmodel // pragma: allowlist secret

import compose.project.click.click.data.api.BeaconAttendeeDto // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.ui.components.MapPin // pragma: allowlist secret
import compose.project.click.click.ui.utils.CommunityHubPin // pragma: allowlist secret
import compose.project.click.click.ui.utils.ConnectionMapPoint // pragma: allowlist secret
import compose.project.click.click.ui.utils.MapCluster // pragma: allowlist secret

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
    val requestStatus: compose.project.click.click.events.EventRsvpRequestStatus? = null,
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
    val hubId: String? = null,
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
