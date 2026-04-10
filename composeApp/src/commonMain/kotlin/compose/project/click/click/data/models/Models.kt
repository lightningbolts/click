package compose.project.click.click.data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement

@Serializable
data class GeoLocation(
    val lat: Double,
    val lon: Double
)

/**
 * Insert DTO for the geo_location JSON column.
 * Identical to GeoLocation but kept separate so the insert schema is self-documenting.
 */
@Serializable
data class GeoLocationInsert(
    val lat: Double,
    val lon: Double
)

/**
 * Insert DTO for the `connections` table.
 * Using a typed data class instead of Map<String, Any> so that
 * kotlinx.serialization can handle it on Kotlin/Native (iOS).
 */
@Serializable
data class ConnectionInsert(
    val user_ids: List<String>,
    val created: Long,
    /** Legacy column retained for API compatibility; do not use for client chat access or UI gating. */
    val expiry: Long,
    val should_continue: List<Boolean>,
    val has_begun: Boolean,
    val expiry_state: String,
    val include_in_business_insights: Boolean = true,
    val initiator_id: String? = null,
    val responder_id: String? = null
)

/**
 * Minimal profile surface for connection UX (e.g. proximity context tagging).
 * Distinct from [UserPublicProfile], which is a hydrated viewer payload.
 */
data class UserProfile(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
)

fun User.toUserProfile(): UserProfile {
    val display = name?.trim()?.takeIf { it.isNotEmpty() }
        ?: listOfNotNull(firstName?.trim(), lastName?.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .takeIf { it.isNotEmpty() }
        ?: "User"
    return UserProfile(id = id, displayName = display, avatarUrl = image)
}

@Serializable
data class User(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val image: String? = null,
    @SerialName("created_at")
    val createdAt: Long = 0L,  // Made optional with default for schema compatibility
    @SerialName("last_polled")
    val lastPolled: Long? = null,
    @SerialName("first_name")
    val firstName: String? = null,
    @SerialName("last_name")
    val lastName: String? = null,
    /** ISO calendar date string from DB (yyyy-MM-dd) when present. */
    val birthday: String? = null,
    val connections: List<String> = emptyList(),
    val paired_with: List<String> = emptyList(),
    val connection_today: Int = -1,
    val last_paired: Long? = null,
    // Interest tags for Common Ground feature (e.g., "music", "coding", "hiking")
    val tags: List<String> = emptyList()
) {
    /**
     * Convert to insert DTO for Supabase (minimal - only required columns)
     */
    fun toInsertDto() = UserInsertMinimal(
        id = id,
        name = name ?: "User",
        email = email ?: "",
        created_at = if (createdAt > 0L) createdAt else kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    )
}

/**
 * Minimal DTO for inserting user records into Supabase
 * Only includes columns that definitely exist in the table
 */
@Serializable
data class UserInsertMinimal(
    val id: String,
    val name: String,
    val email: String,
    @SerialName("created_at")
    val created_at: Long
)

/**
 * Core user data for fetching from database
 * Only includes columns guaranteed to exist in the users table
 */
/**
 * Denormalized availability intent bubble on [public.users.availability_intents] (JSON array).
 */
@Serializable
data class ProfileAvailabilityIntentBubble(
    @SerialName("intent_tag")
    val intentTag: String? = null,
    val timeframe: String? = null,
    @SerialName("expires_at")
    val expiresAt: String? = null,
)

@Serializable
data class UserCore(
    val id: String,
    val name: String? = null,
    val full_name: String? = null,
    @SerialName("first_name")
    val firstName: String? = null,
    @SerialName("last_name")
    val lastName: String? = null,
    val birthday: String? = null,
    val email: String? = null,
    val image: String? = null,
    @SerialName("last_polled")
    val lastPolled: Long? = null,
) {
    /**
     * Convert to full User model with defaults for missing fields.
     * Resolves display name: first+last > full_name > name > email prefix > "Connection".
     */
    fun toUser(): User {
        val resolvedName = resolveDisplayName(
            firstName = firstName,
            lastName = lastName,
            fullName = full_name,
            name = name,
            email = email
        )
        return User(
            id = id,
            name = resolvedName,
            email = email,
            image = image,
            createdAt = 0L,
            lastPolled = lastPolled,
            firstName = firstName,
            lastName = lastName,
            birthday = birthday,
            connections = emptyList(),
            paired_with = emptyList(),
            connection_today = -1,
            last_paired = null
        )
    }
}

fun resolveDisplayName(
    firstName: String? = null,
    lastName: String? = null,
    fullName: String?,
    name: String?,
    email: String?
): String {
    fun normalizedCandidate(value: String?): String? {
        return value
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("Connection", ignoreCase = true) }
    }

    val fromParts = listOfNotNull(
        normalizedCandidate(firstName),
        normalizedCandidate(lastName)
    ).joinToString(" ").trim()
    if (fromParts.isNotEmpty()) return fromParts

    return normalizedCandidate(fullName)
        ?: normalizedCandidate(name)
        ?: normalizedCandidate(email?.substringBefore('@'))
        ?: "Connection"
}

fun isResolvedDisplayName(value: String?): Boolean {
    return value
        ?.trim()
        ?.let { it.isNotEmpty() && !it.equals("Connection", ignoreCase = true) }
        ?: false
}

/**
 * Location privacy preferences stored on the user's Supabase profile row.
 * Ghost mode (AppDataManager.ghostModeEnabled) overrides all when active.
 */
@Serializable
data class LocationPreferences(
    @SerialName("location_connection_snap_enabled")
    val connectionSnapEnabled: Boolean = true,
    @SerialName("location_show_on_map_enabled")
    val showOnMapEnabled: Boolean = true,
    @SerialName("location_include_in_insights_enabled")
    val includeInInsightsEnabled: Boolean = true
)

/** One row per user in `public.user_interests`; canonical source for interest tags and onboarding completion. */
@Serializable
data class UserInterests(
    @SerialName("user_id")
    val userId: String,
    val tags: List<String> = emptyList(),
    @SerialName("updated_at")
    val updatedAt: Long = 0L,
)

/** Hydrated profile for the profile viewer (connections may read via RLS). */
data class UserPublicProfile(
    val user: User,
    val interestTags: List<String>,
    val availability: UserAvailability?,
    /** From [public.users.availability_intents]; may be empty when unset or expired server-side. */
    val profileAvailabilityIntents: List<ProfileAvailabilityIntentBubble> = emptyList(),
    /** Logged-in viewer's interest tags (for shared-interest intersection in profile sheet). */
    val viewerInterestTags: List<String> = emptyList(),
    /** Mutual `connections` row (most recently active), when viewer is known. */
    val sharedConnection: Connection? = null,
)

@Serializable
data class Message(
    val id: String,
    val user_id: String,
    val content: String,
    @SerialName("time_created")
    val timeCreated: Long,
    @SerialName("time_edited")
    val timeEdited: Long? = null,
    @SerialName("is_read")
    val isRead: Boolean = false,
    /** See [compose.project.click.click.data.models.ChatMessageType] — e.g. `text`, `image`, `audio`, `call_log`. */
    @SerialName("message_type")
    val messageType: String = "text",
    /** JSON payload; for media messages typically includes `media_url` and optional `duration_seconds`. */
    val metadata: JsonElement? = null,
)

@Serializable
data class MessageReaction(
    val id: String,
    @SerialName("message_id")
    val messageId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("reaction_type")
    val reactionType: String,
    @SerialName("created_at")
    val createdAt: Long
)

@Serializable
data class ConnectionRequest(
    val userId1: String,
    val userId2: String,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    /** When set (e.g. from token QR), device GPS is not sent; backend resolves location for the venue. */
    val venueId: String? = null,
    val altitudeMeters: Double? = null,
    val heightCategory: HeightCategory? = null,
    val exactBarometricElevationMeters: Double? = null,
    val exactBarometricPressureHpa: Double? = null,
    val contextTag: String? = null, // User-defined tag like "Met at Dawg Daze"
    val contextTagObject: ContextTag? = null,
    val connectionMethod: String = "qr", // "qr" | "proximity" | legacy "nfc"
    val tokenAgeMs: Long? = null, // Milliseconds since QR token was created (null for NFC/legacy)
    val qrToken: String? = null,
    val initiatorId: String? = null,
    val responderId: String? = null,
    val noiseLevelCategory: NoiseLevelCategory? = null,
    val exactNoiseLevelDb: Double? = null
)

@Serializable
data class Chat(
    val id: String? = null,
    @SerialName("connection_id")
    val connectionId: String? = null,
    @SerialName("group_id")
    val groupId: String? = null,
    val messages: List<Message> = emptyList()
)

/** Metadata for a mathematically verified group clique chat (pairwise connections only). */
data class GroupCliqueDetails(
    val groupId: String,
    val name: String,
    val createdByUserId: String,
    val keyAnchorUserId: String,
    val memberUserIds: List<String>,
)

/**
 * Represents a connection between two users.
 * Matches the Python schema.py Connection class.
 */
@Serializable
data class Connection(
    val id: String,
    val created: Long,
    @SerialName("created_utc")
    val createdUtc: String? = null,
    @SerialName("time_of_day_utc")
    val timeOfDayUtc: String? = null,
    /** Legacy server timestamp; ignored for client-side chat access (use archive lifecycle / [connection_archives]). */
    val expiry: Long,
    /** Legacy: graph edge only — prefer [connectionEncounters] for place / GPS. */
    val geo_location: GeoLocation? = null,
    val full_location: Map<String, String>? = null,
    val semantic_location: String? = null,
    @SerialName("initiator_id")
    val initiatorId: String? = null,
    @SerialName("responder_id")
    val responderId: String? = null,
    @SerialName("memory_capsule")
    val memoryCapsule: MemoryCapsule? = null,
    @SerialName("noise_level")
    val noiseLevel: String? = null,
    @SerialName("exact_noise_level_db")
    val exactNoiseLevelDb: Double? = null,
    @SerialName("height_category")
    val heightCategory: String? = null,
    @SerialName("exact_barometric_elevation_m")
    val exactBarometricElevationM: Double? = null,
    @SerialName("weather_condition")
    val weatherCondition: String? = null,
    @SerialName("context_tag_id")
    val contextTagId: String? = null,
    /** Timeline of crossings; populated when selecting `connection_encounters` or merged client-side. */
    @SerialName("connection_encounters")
    val connectionEncounters: List<ConnectionEncounter> = emptyList(),
    val user_ids: List<String>,
    val chat: Chat = Chat(),
    val should_continue: List<Boolean> = listOf(false, false),
    val has_begun: Boolean = false,
    // Server-side expiry lifecycle: 'pending' | 'active' | 'kept' | 'expired' (mirrors status via DB trigger)
    val expiry_state: String = "pending",
    /**
     * Canonical lifecycle: pending | active | kept | archived | removed.
     * Null when older API responses omit the column — use [normalizedConnectionStatus].
     */
    val status: String? = null,
    // Timestamp (ms) of the most recent message in this connection's chat
    val last_message_at: Long? = null,
    // Proximity verification fields
    val proximity_confidence: Int = 100,
    val proximity_signals: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    val connection_method: String = "qr",
    val flagged: Boolean = false,
    val include_in_business_insights: Boolean = true
) {
    companion object {
        // 30 minutes in milliseconds for the Vibe Check timer
        const val VIBE_CHECK_DURATION_MS = 30L * 60 * 1000
        // 48 hours in milliseconds for the pending "Say Hi" window
        const val PENDING_DURATION_MS = 48L * 60 * 60 * 1000
        // 7-day rolling window for continued interaction (after first message)
        const val IDLE_ARCHIVE_DURATION_MS = 7L * 24 * 60 * 60 * 1000
    }

    fun originEncounter(): ConnectionEncounter? =
        connectionEncounters.minByOrNull { it.encounteredAt }

    fun latestEncounter(): ConnectionEncounter? =
        connectionEncounters.maxByOrNull { it.encounteredAt }

    /** Origin story (oldest crossing) for profile / first-meet copy. */
    fun originMemoryCapsule(): MemoryCapsule? =
        originEncounter()?.toMemoryCapsule() ?: memoryCapsule

    /** Newest crossing for “where we last saw each other” surfaces. */
    fun latestMemoryCapsule(): MemoryCapsule? =
        latestEncounter()?.toMemoryCapsule() ?: memoryCapsule

    val context_tag: String?
        get() = originMemoryCapsule()?.contextTag?.label
            ?: contextTagId
            ?: originEncounter()?.contextTags?.firstOrNull()

    val resolvedNoiseLevel: String?
        get() = originEncounter()?.noiseLevel?.trim()?.takeIf { it.isNotEmpty() }
            ?: noiseLevel ?: memoryCapsule?.noiseLevelCategory?.name

    val resolvedExactNoiseLevelDb: Double?
        get() = exactNoiseLevelDb ?: memoryCapsule?.exactNoiseLevelDb

    val resolvedHeightCategory: String?
        get() = originEncounter()?.elevationCategory?.trim()?.takeIf { it.isNotEmpty() }
            ?: heightCategory ?: memoryCapsule?.heightCategory?.name

    val resolvedExactBarometricElevationM: Double?
        get() = exactBarometricElevationM ?: memoryCapsule?.exactBarometricElevationMeters

    val resolvedWeatherCondition: String?
        get() = originEncounter()?.weatherSnapshot?.condition?.trim()?.takeIf { it.isNotEmpty() }
            ?: weatherCondition ?: memoryCapsule?.weatherSnapshot?.condition

    val displayLocationLabel: String?
        get() = context_tag
            ?: latestEncounter()?.locationName?.trim()?.takeIf { it.isNotEmpty() }
            ?: semantic_location

    /** Resolved status when [status] column is missing (legacy rows). */
    fun normalizedConnectionStatus(): String {
        status?.takeIf { it.isNotBlank() }?.let { return it }
        return when (expiry_state) {
            "kept" -> "kept"
            "active" -> "active"
            "expired" -> "archived"
            else -> "pending"
        }
    }
    
    /** Connection is awaiting first message (48h window). */
    fun isPending(): Boolean = normalizedConnectionStatus() == "pending"

    /** Connection is active — messages flowing, 7-day rolling window. */
    fun isActive(): Boolean = normalizedConnectionStatus() == "active"

    /** Connection permanently kept via mutual opt-in. */
    fun isKept(): Boolean = normalizedConnectionStatus() == "kept"

    fun isArchivedOrRemoved(): Boolean {
        val s = normalizedConnectionStatus()
        return s == "archived" || s == "removed"
    }

    /** Server/cron `status = archived` (distinct from per-user hide via `connection_archives`). */
    fun isServerLifecycleArchived(): Boolean = normalizedConnectionStatus() == "archived"

    /**
     * Connections that belong in the Clicks "Active" channel: pending, active, or kept only.
     */
    fun isInActiveConnectionsChannel(): Boolean {
        val s = normalizedConnectionStatus()
        return s == "pending" || s == "active" || s == "kept"
    }

    /** Visible in main UI (not soft-deleted / auto-archived). */
    fun isVisibleInActiveUi(): Boolean = !isArchivedOrRemoved()

    /**
     * Calculate remaining time in the 48-hour pending window.
     * Returns remaining milliseconds, or 0 if time has expired.
     */
    fun getPendingRemainingMs(currentTimeMs: Long): Long {
        val endTime = created + PENDING_DURATION_MS
        return maxOf(0L, endTime - currentTimeMs)
    }

    /**
     * Remaining ms before 7-day idle archive (active only). Uses [last_message_at] when set, else [created].
     */
    fun getIdleArchiveRemainingMs(currentTimeMs: Long): Long {
        if (normalizedConnectionStatus() != "active") return Long.MAX_VALUE
        val anchor = last_message_at ?: created
        val end = anchor + IDLE_ARCHIVE_DURATION_MS
        return maxOf(0L, end - currentTimeMs)
    }

    /**
     * Calculate the remaining time for the Vibe Check.
     * Returns remaining milliseconds, or 0 if time has expired.
     */
    fun getVibeCheckRemainingMs(currentTimeMs: Long): Long {
        val endTime = created + VIBE_CHECK_DURATION_MS
        return maxOf(0L, endTime - currentTimeMs)
    }
    
    /**
     * Check if the Vibe Check timer has expired.
     */
    fun isVibeCheckExpired(currentTimeMs: Long): Boolean {
        return getVibeCheckRemainingMs(currentTimeMs) == 0L
    }
    
    /**
     * Check if both users have opted to keep the connection.
     */
    fun isMutuallyKept(): Boolean {
        return should_continue.size >= 2 && should_continue[0] && should_continue[1]
    }
    
    /**
     * Get the index for a user in the should_continue list based on their position in user_ids.
     */
    fun getUserIndex(userId: String): Int? {
        return user_ids.indexOf(userId).takeIf { it >= 0 }
    }

    /** Map-ready coordinates: legacy [geo_location], then latest encounter GPS. */
    fun connectionMapGeo(): GeoLocation? {
        val direct = geo_location
        if (direct != null && direct.lat.isFinite() && direct.lon.isFinite() &&
            !(direct.lat == 0.0 && direct.lon == 0.0)
        ) {
            return direct
        }
        val e = latestEncounter() ?: originEncounter()
        val la = e?.gpsLat
        val lo = e?.gpsLon
        if (la != null && lo != null && la.isFinite() && lo.isFinite() &&
            !(la == 0.0 && lo == 0.0)
        ) {
            return GeoLocation(lat = la, lon = lo)
        }
        return latestMemoryCapsule()?.geoLocation?.takeIf { g ->
            g.lat.isFinite() && g.lon.isFinite() && !(g.lat == 0.0 && g.lon == 0.0)
        }
    }
}

/**
 * Clicks "Active" tab / home active map: exclude [connection_hidden] and [connection_archives]
 * for this user, and only pending/active/kept lifecycle rows.
 */
fun Connection.isActiveForUser(archivedIds: Set<String>, hiddenIds: Set<String>): Boolean =
    id !in hiddenIds && id !in archivedIds && isInActiveConnectionsChannel()

/**
 * Clicks "Archived" tab: server-archived lifecycle or user junction archive, never hidden.
 */
fun Connection.isArchivedChannelForUser(archivedIds: Set<String>, hiddenIds: Set<String>): Boolean =
    id !in hiddenIds && (isServerLifecycleArchived() || id in archivedIds)

/** Placeholder [Connection] so group cliques participate in chat list / [ChatWithDetails] flows. */
fun syntheticConnectionForGroupClique(
    groupId: String,
    memberUserIds: List<String>,
    lastMessageAt: Long? = null,
): Connection = Connection(
    id = groupId,
    created = 0L,
    expiry = Long.MAX_VALUE,
    geo_location = GeoLocation(lat = 0.0, lon = 0.0),
    user_ids = memberUserIds.distinct(),
    status = "kept",
    last_message_at = lastMessageAt,
)

// UI models for chat functionality
data class ChatWithDetails(
    val chat: Chat,
    val connection: Connection,
    val otherUser: User,
    val lastMessage: Message?,
    val unreadCount: Int,
    /** When non-null, [connection] is a synthetic row keyed by [GroupCliqueDetails.groupId]. */
    val groupClique: GroupCliqueDetails? = null,
    /**
     * For [groupClique] chats: other members (excluding viewer), sorted for stable list avatars.
     * Empty for 1:1 chats.
     */
    val groupMemberUsers: List<User> = emptyList(),
)

data class MessageWithUser(
    val message: Message,
    val user: User,
    val isSent: Boolean
)
