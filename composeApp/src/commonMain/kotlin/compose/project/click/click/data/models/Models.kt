package compose.project.click.click.data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class GeoLocation(
    val lat: Double,
    val lon: Double
)

@Serializable
data class User(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val image: String? = null,
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("last_polled")
    val lastPolled: Long? = null,
    val connections: List<String> = emptyList(),
    val paired_with: List<String> = emptyList(),
    val connection_today: Int = -1,
    val last_paired: Long? = null
)

@Serializable
data class Message(
    val id: String,
    val user_id: String,
    val content: String,
    @SerialName("time_created")
    val timeCreated: Long,
    @SerialName("time_edited")
    val timeEdited: Long? = null
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
    val locationLng: Double? = null
)

@Serializable
data class Chat(
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
    val user_ids: List<String>,
    val chat: Chat = Chat(),
    val should_continue: List<Boolean> = listOf(false, false),
    val has_begun: Boolean = false
)

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
