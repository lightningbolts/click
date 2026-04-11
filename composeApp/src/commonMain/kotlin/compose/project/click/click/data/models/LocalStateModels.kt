package compose.project.click.click.data.models

import compose.project.click.click.sensors.HardwareVibeSnapshot
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

const val PENDING_SYNC_CONNECTION_PREFIX = "pending-sync:"

/** Bump when onboarding steps or gating rules change so clients re-run the full flow once. */
const val ONBOARDING_FLOW_VERSION_COMPLETE = 2

@Serializable
data class OnboardingState(
    /**
     * Set to [ONBOARDING_FLOW_VERSION_COMPLETE] only after interests + permissions onboarding finishes.
     * Older stored states default to 0 and are treated as needing the current flow.
     */
    val flowVersion: Int = 0,
    val permissionsCompleted: Boolean = false,
    val interestsCompleted: Boolean = false,
    val locationPermissionRequested: Boolean = false,
    val notificationPermissionRequested: Boolean = false,
    val microphonePermissionRequested: Boolean = false,
    val barometricContextPermissionReviewed: Boolean = false,
    val completedAt: Long? = null,
) {
    val isComplete: Boolean
        get() = flowVersion >= ONBOARDING_FLOW_VERSION_COMPLETE && permissionsCompleted && interestsCompleted
}

/**
 * Cold-start snapshot. Subjective proximity tags are not staged here; they flow from
 * [compose.project.click.click.viewmodel.ConnectionState.TaggingContext] into each `connections` row after creation.
 */
@Serializable
data class CachedAppSnapshot(
    val currentUser: User? = null,
    val connections: List<Connection> = emptyList(),
    val connectedUsers: List<User> = emptyList(),
    val locationPreferences: LocationPreferences = LocationPreferences(),
    val archivedConnectionIds: Set<String> = emptySet(),
    val hiddenConnectionIds: Set<String> = emptySet(),
)

@Serializable
data class PendingConnectionDraft(
    val localId: String,
    val request: ConnectionRequest,
    val queuedAt: Long,
    val otherUserName: String? = null,
) {
    fun toPlaceholderConnection(includeInInsights: Boolean): Connection {
        val queuedInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(queuedAt)
        val queuedTime = queuedInstant.toLocalDateTime(TimeZone.UTC)
        val validLat = request.locationLat?.takeIf { it.isFinite() } ?: 0.0
        val validLon = request.locationLng?.takeIf { it.isFinite() } ?: 0.0

        return Connection(
            id = localId,
            created = queuedAt,
            createdUtc = queuedInstant.toString(),
            timeOfDayUtc = "${queuedTime.hour.toString().padStart(2, '0')}:${queuedTime.minute.toString().padStart(2, '0')}:${queuedTime.second.toString().padStart(2, '0')} UTC",
            // Placeholder for legacy `connections.expiry` serialization; not used for UI gating.
            expiry = queuedAt + (30L * 24 * 60 * 60 * 1000),
            geo_location = GeoLocation(lat = validLat, lon = validLon),
            contextTagId = request.contextTagObject?.label ?: request.contextTag,
            initiatorId = request.initiatorId,
            responderId = request.responderId,
            user_ids = listOf(request.userId1, request.userId2),
            noiseLevel = request.noiseLevelCategory?.name,
            exactNoiseLevelDb = request.exactNoiseLevelDb,
            heightCategory = request.heightCategory?.name,
            exactBarometricElevationM = request.exactBarometricElevationMeters,
            should_continue = listOf(false, false),
            has_begun = false,
            expiry_state = "pending",
            proximity_confidence = 0,
            connection_method = request.connectionMethod,
            flagged = false,
            include_in_business_insights = includeInInsights,
        )
    }
}

fun Connection.isPendingSync(): Boolean = id.startsWith(PENDING_SYNC_CONNECTION_PREFIX)

fun newPendingConnectionId(): String = "$PENDING_SYNC_CONNECTION_PREFIX${Clock.System.now().toEpochMilliseconds()}"

/**
 * GPS snapshot at tri-factor tap time for deferred [bind-proximity-connection] replay.
 */
@Serializable
data class ProximityHandshakeLocationSnapshot(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val capturedAtEpochMs: Long,
)

/**
 * Offline queue item: BLE/ultrasonic tokens + optional location for server-side clustering when back online.
 */
@Serializable
data class PendingHandshake(
    val id: String,
    val myToken: String,
    val heardTokens: List<String>,
    val capturedAtEpochMs: Long,
    val location: ProximityHandshakeLocationSnapshot? = null,
    val hardwareVibe: HardwareVibeSnapshot? = null,
)

fun newPendingHandshakeId(): String =
    "pending-handshake:${Clock.System.now().toEpochMilliseconds()}:${kotlin.random.Random.nextInt(0, 10_000)}"
