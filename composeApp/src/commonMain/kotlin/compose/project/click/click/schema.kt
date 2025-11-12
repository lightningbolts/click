package compose.project.click.click

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Note: These data classes are Kotlin translations of the provided Python schema.
 * Business logic and default value generation (like UUIDs or timestamps) from the original
 * Python classes have been separated. You would typically handle ID generation and
 * timestamping in your app's business logic layer (e.g., a repository or view model).
 */

@Serializable
data class GeoLocation(
    val lat: Double,
    val lon: Double
)

@Serializable
data class LoginObject(
    val jwt: String,
    val refresh: String,
    val user: User
)

/**
 * Represents a user in the system.
 * Translated from the Python `User` class.
 */
@Serializable
data class User(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val image: String? = null,
    val createdAt: Instant? = null,
    val lastPolled: Instant? = null,
    val connections: List<String> = emptyList(),
    val paired_with: List<String> = emptyList(),
    val connection_today: Int = -1,
    val last_paired: Instant? = null
)

/**
 * Represents a single message in a chat.
 * Translated from the Python `Message` class.
 */
@Serializable
data class Message(
    val id: String,
    val user_id: String,
    val content: String,
    val timeCreated: Instant? = null,
    val timeEdited: Instant? = null
)

/**
 * Represents a chat session containing messages.
 * Translated from the Python `Chat` class.
 */
@Serializable
data class Chat(
    val messages: List<Message> = emptyList()
)

/**
 * Represents a connection between two users.
 * The Python `get_semantic_location` is not translated; you'll need a Kotlin implementation for that.
 */
@Serializable
data class Connection(
    val id: String,
    val created: Instant? = null,
    val expiry: Instant? = null,
    val geo_location: GeoLocation,
    // Represents the full JSON object from a semantic lookup, equivalent to `get_semantic_location()`
    val full_location: Map<String, String>? = null,
    // Represents the 'display_name' from the semantic location lookup
    val semantic_location: String? = null,
    val user_ids: List<String>,
    val chat: Chat = Chat(),
    val should_continue: List<Boolean> = listOf(false, false),
    val has_begun: Boolean = false
)