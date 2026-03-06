package compose.project.click.click.data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

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
    val geo_location: GeoLocationInsert,
    val created: Long,
    val expiry: Long,
    val should_continue: List<Boolean>,
    val has_begun: Boolean,
    val expiry_state: String,
    val context_tag: String? = null
)

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
@Serializable
data class UserCore(
    val id: String,
    val name: String? = null,
    val full_name: String? = null,
    val email: String? = null,
    val image: String? = null,
    @SerialName("last_polled")
    val lastPolled: Long? = null
) {
    /**
     * Convert to full User model with defaults for missing fields.
     * Resolves display name: full_name > name > email prefix > "Connection".
     */
    fun toUser(): User {
        val resolvedName = full_name?.trim()?.takeIf { it.isNotEmpty() }
            ?: name?.trim()?.takeIf { it.isNotEmpty() }
            ?: email?.substringBefore('@')?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Connection"
        return User(
            id = id,
            name = resolvedName,
            email = email,
            image = image,
            createdAt = 0L,
            lastPolled = lastPolled,
            connections = emptyList(),
            paired_with = emptyList(),
            connection_today = -1,
            last_paired = null
        )
    }
}

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
    val isRead: Boolean = false
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
    val contextTag: String? = null, // User-defined tag like "Met at Dawg Daze"
    val connectionMethod: String = "qr", // "qr" or "nfc"
    val tokenAgeMs: Long? = null // Milliseconds since QR token was created (null for NFC/legacy)
)

@Serializable
data class Chat(
    val id: String? = null,
    @SerialName("connection_id")
    val connectionId: String? = null,
    val messages: List<Message> = emptyList()
)

/**
 * Represents a connection between two users.
 * Matches the Python schema.py Connection class.
 */
@Serializable
data class Connection(
    val id: String,
    val created: Long,
    val expiry: Long,
    // Geographic location as lat/lon coordinate pair
    val geo_location: GeoLocation,
    // Full JSON object from semantic location lookup (e.g., from Nominatim)
    val full_location: Map<String, String>? = null,
    // Display name from the semantic location lookup (e.g., "Red Square")
    val semantic_location: String? = null,
    // User-defined context tag (e.g., "Met at Dawg Daze", "CSE 142")
    val context_tag: String? = null,
    val user_ids: List<String>,
    val chat: Chat = Chat(),
    val should_continue: List<Boolean> = listOf(false, false),
    val has_begun: Boolean = false,
    // Server-side expiry lifecycle: 'pending' | 'active' | 'kept' | 'expired'
    val expiry_state: String = "pending",
    // Timestamp (ms) of the most recent message in this connection's chat
    val last_message_at: Long? = null,
    // Proximity verification fields
    val proximity_confidence: Int = 100,
    val proximity_signals: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    val connection_method: String = "qr",
    val flagged: Boolean = false
) {
    companion object {
        // 30 minutes in milliseconds for the Vibe Check timer
        const val VIBE_CHECK_DURATION_MS = 30L * 60 * 1000
        // 48 hours in milliseconds for the pending "Say Hi" window
        const val PENDING_DURATION_MS = 48L * 60 * 60 * 1000
    }
    
    /** Connection is awaiting first message (48h window). */
    fun isPending(): Boolean = expiry_state == "pending"

    /** Connection is active — messages flowing, 7-day rolling window. */
    fun isActive(): Boolean = expiry_state == "active"

    /** Connection permanently kept via mutual opt-in. */
    fun isKept(): Boolean = expiry_state == "kept"

    /**
     * Calculate remaining time in the 48-hour pending window.
     * Returns remaining milliseconds, or 0 if time has expired.
     */
    fun getPendingRemainingMs(currentTimeMs: Long): Long {
        val endTime = created + PENDING_DURATION_MS
        return maxOf(0L, endTime - currentTimeMs)
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
}

// UI models for chat functionality
data class ChatWithDetails(
    val chat: Chat,
    val connection: Connection,
    val otherUser: User,
    val lastMessage: Message?,
    val unreadCount: Int
)

data class MessageWithUser(
    val message: Message,
    val user: User,
    val isSent: Boolean
)
