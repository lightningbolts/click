package compose.project.click.click.data.models

import compose.project.click.click.sensors.HardwareVibeSnapshot
import kotlinx.serialization.SerialName
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
    // Phase 2 B2 — Welcome → Interests → Avatar → Complete (permissions go contextual).
    // Defaults are backwards-compatible: pre-existing states see `welcomeSeen = false` and
    // `avatarSetOrSkipped = false`, which the Phase 2 OnboardingViewModel layers on top without
    // forcing a re-onboarding for accounts that already completed the legacy flow
    // (see [OnboardingViewModel.needsPhase2Onboarding]).
    val welcomeSeen: Boolean = false,
    val avatarSetOrSkipped: Boolean = false,
) {
    /** Legacy (Phase 1) completion predicate. Kept as-is so the existing migration path in App.kt
     * continues to recognise already-onboarded accounts while we roll out B2. */
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
    val cachedChatThreads: List<CachedChatThread> = emptyList(),
    /** Hydrated user profiles restored on cold start so profile sheets can open without a network wait. */
    val cachedUserPublicProfiles: List<UserPublicProfile> = emptyList(),
    /**
     * Last successful unified inbox (direct + group rows) for instant Clicks list paint on cold start.
     */
    val inboxFeedChats: List<ChatWithDetails> = emptyList(),
)

/**
 * Locally decrypted chat payload cached after sync. This is persisted inside the encrypted
 * app snapshot so chat navigation can paint from disk without waiting on network or E2EE.
 */
@Serializable
data class CachedChatThread(
    val connectionId: String,
    val chatId: String,
    val cachedAtMs: Long,
    val messages: List<Message> = emptyList(),
    val participants: List<User> = emptyList(),
    val reactions: List<MessageReaction> = emptyList(),
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
    @SerialName("noise_level") val noiseLevel: String? = null,
    @SerialName("exact_noise_level_db") val exactNoiseLevelDb: Double? = null,
    @SerialName("height_category") val heightCategory: String? = null,
    @SerialName("exact_barometric_elevation_m") val exactBarometricElevationM: Double? = null,
    @SerialName("context_tags") val contextTags: List<String> = emptyList(),
)

fun newPendingHandshakeId(): String =
    "pending-handshake:${Clock.System.now().toEpochMilliseconds()}:${kotlin.random.Random.nextInt(0, 10_000)}"
