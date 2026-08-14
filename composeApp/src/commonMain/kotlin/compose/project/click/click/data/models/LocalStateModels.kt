@file:Suppress("ktlint:standard:max-line-length")

package compose.project.click.click.data.models // pragma: allowlist secret

import compose.project.click.click.sensors.HardwareVibeSnapshot // pragma: allowlist secret
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
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
    /**
     * New signups pick exactly 5 personality traits after interests.
     * Defaults false; [OnboardingViewModel] skips the step for legacy-complete accounts.
     */
    val personalityCompleted: Boolean = false,
    /**
     * New signups may optionally hash contacts after avatar. Defaults false;
     * [OnboardingViewModel] skips the step for legacy-complete accounts.
     */
    val priorConnectionsSetOrSkipped: Boolean = false,
) {
    /**
     * Completion predicate for cold-start / restore.
     * Phase 1: permissions + interests.
     * Phase 2: Welcome + interests + avatar (permissions are contextual).
     */
    val isComplete: Boolean
        get() =
            flowVersion >= ONBOARDING_FLOW_VERSION_COMPLETE &&
                interestsCompleted &&
                (permissionsCompleted || (welcomeSeen && avatarSetOrSkipped))
}

/**
 * Fully-hydrated onboarding state for returning users — skips Welcome and marks prior steps done.
 * Used when [TokenStorage.getHasCompletedOnboarding] is true or remote profile/interests prove
 * an existing account (prevents the Welcome flash on cold start).
 */
fun existingHydratedOnboardingState(permissionsCompleted: Boolean = true): OnboardingState =
    OnboardingState(
        flowVersion = ONBOARDING_FLOW_VERSION_COMPLETE,
        permissionsCompleted = permissionsCompleted,
        interestsCompleted = true,
        welcomeSeen = true,
        personalityCompleted = true,
        avatarSetOrSkipped = true,
        priorConnectionsSetOrSkipped = true,
    )

/** Seed for returning accounts that must never see Welcome again. */
fun skipWelcomeExistingUserState(
    interestsCompleted: Boolean,
    permissionsCompleted: Boolean = false,
): OnboardingState =
    OnboardingState(
        flowVersion = if (interestsCompleted) ONBOARDING_FLOW_VERSION_COMPLETE else 0,
        permissionsCompleted = permissionsCompleted,
        interestsCompleted = interestsCompleted,
        welcomeSeen = true,
        personalityCompleted = interestsCompleted,
        avatarSetOrSkipped = interestsCompleted,
        priorConnectionsSetOrSkipped = interestsCompleted,
    )

fun isExistingAccountForOnboarding(
    tagCount: Int,
    userFirstName: String?,
    userBirthday: String?,
    userAvatarUrl: String?,
    minInterestTags: Int,
): Boolean =
    tagCount >= minInterestTags ||
        (!userFirstName.isNullOrBlank() && !userBirthday.isNullOrBlank()) ||
        !userAvatarUrl.isNullOrBlank()

/**
 * Disk hydration before remote interests/profile resolve.
 * Returns null when no saved state and onboarding is not marked complete — caller keeps
 * [onboardingState] null so the loading shimmer stays up.
 */
fun resolveOnboardingInitialState(
    savedState: OnboardingState?,
    hasCompletedOnboarding: Boolean?,
): OnboardingState? {
    val completed = hasCompletedOnboarding == true
    if (savedState != null) {
        var state = savedState
        if (completed) {
            state =
                state.copy(
                    welcomeSeen = true,
                    permissionsCompleted = state.permissionsCompleted || completed,
                )
        }
        return state
    }
    if (completed) {
        return existingHydratedOnboardingState()
    }
    return null
}

/**
 * After [fetchUserInterests] (and optional profile signals), decide whether the account is new
 * or returning and merge/persist the correct [OnboardingState].
 */
fun resolveOnboardingAfterRemoteResolution(
    currentState: OnboardingState?,
    tagCount: Int,
    userFirstName: String?,
    userBirthday: String?,
    userAvatarUrl: String?,
    minInterestTags: Int,
): OnboardingState {
    val interestsSatisfied = tagCount >= minInterestTags
    val isExisting =
        isExistingAccountForOnboarding(
            tagCount = tagCount,
            userFirstName = userFirstName,
            userBirthday = userBirthday,
            userAvatarUrl = userAvatarUrl,
            minInterestTags = minInterestTags,
        )

    if (currentState != null) {
        if (!isExisting) {
            return currentState
        }
        var merged =
            currentState.copy(
                welcomeSeen = true,
            )
        if (interestsSatisfied && !merged.interestsCompleted) {
            merged =
                merged.copy(
                    interestsCompleted = true,
                    flowVersion = merged.flowVersion.coerceAtLeast(ONBOARDING_FLOW_VERSION_COMPLETE),
                )
        }
        return merged
    }

    return if (isExisting) {
        skipWelcomeExistingUserState(interestsCompleted = interestsSatisfied)
    } else {
        OnboardingState()
    }
}

/**
 * Cold-start snapshot. Subjective proximity tags are not staged here; they flow from
 * [ConnectionState.TaggingContext] into each `connections` row after creation.
 */
@Serializable
data class CachedAppSnapshot(
    val currentUser: User? = null,
    val connections: List<Connection> = emptyList(),
    val connectedUsers: List<User> = emptyList(),
    val locationPreferences: LocationPreferences = LocationPreferences(),
    val archivedConnectionIds: Set<String> = emptySet(),
    val hiddenConnectionIds: Set<String> = emptySet(),
    val coreConnectionIds: Set<String> = emptySet(),
    val cachedChatThreads: List<CachedChatThread> = emptyList(),
    /** Hub venue chat timelines restored on cold start for instant hub navigation paint. */
    val cachedHubThreads: List<CachedHubThread> = emptyList(),
    /** Hydrated user profiles restored on cold start so profile sheets can open without a network wait. */
    val cachedUserPublicProfiles: List<UserPublicProfile> = emptyList(),
    /** Profile/group timeline payloads restored on cold start for instant timeline tab paint. */
    val cachedProfileTimelines: List<ProfileTimelineCacheEntry> = emptyList(),
    /**
     * Last successful unified inbox (direct + group rows) for instant Clicks list paint on cold start.
     */
    val inboxFeedChats: List<ChatWithDetails> = emptyList(),
    /** Cached map discovery beacons for offline map / feed paint. */
    val cachedMapBeacons: List<StoredMapBeacon> = emptyList(),
    /** Cached community hubs for offline map / feed paint. */
    val cachedCommunityHubs: List<StoredCommunityHubPin> = emptyList(),
    /** Saved event bookmarks for instant Home paint on cold start. */
    val cachedEventBookmarks: List<StoredEventBookmark> = emptyList(),
    /** Wall-clock ms when this snapshot was last persisted; drives cold-start inbox freshness. */
    val snapshotSavedAtMs: Long = 0L,
)

@Serializable
data class ProfileTimelineCacheEntry(
    val key: String,
    @SerialName("target_type")
    val targetType: String,
    @SerialName("target_id")
    val targetId: String,
    val cachedAtMs: Long,
    val payload: ProfileTimelinePayload,
)

@Serializable
data class ProfileTimelinePayload(
    @SerialName("target_type")
    val targetType: String,
    @SerialName("target_id")
    val targetId: String,
    @SerialName("shared_interests")
    val sharedInterests: List<GroupSharedInterest> = emptyList(),
    @SerialName("journal_entries")
    val journalEntries: List<ProfileTimelineJournalEntry> = emptyList(),
)

@Serializable
data class GroupSharedInterest(
    val tag: String,
    val count: Int,
    @SerialName("user_ids")
    val userIds: List<String> = emptyList(),
    @SerialName("member_names")
    val memberNames: List<String> = emptyList(),
)

@Serializable
data class ProfileTimelineJournalEntry(
    val id: String,
    @SerialName("target_type")
    val targetType: String,
    @SerialName("target_id")
    val targetId: String,
    @SerialName("author_user_id")
    val authorUserId: String,
    @SerialName("author_name")
    val authorName: String? = null,
    val body: String,
    val visibility: String,
    @SerialName("created_at")
    val createdAt: String,
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

/**
 * Locally cached hub venue messages persisted inside the encrypted app snapshot so hub
 * navigation can paint from disk without waiting on network.
 */
@Serializable
data class CachedHubThread(
    val hubId: String,
    val realtimeChannel: String,
    val cachedAtMs: Long,
    val messages: List<Message> = emptyList(),
    val participants: List<User> = emptyList(),
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
            timeOfDayUtc = "${queuedTime.hour.toString().padStart(
                2,
                '0',
            )}:${queuedTime.minute.toString().padStart(2, '0')}:${queuedTime.second.toString().padStart(2, '0')} UTC",
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
    @SerialName("detected_devices") val detectedDevices: List<String> = emptyList(),
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
