package compose.project.click.click.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where a deleted message used to be (`message_tombstones`, click-web migration
 * 20260924121000). The row itself is hard-deleted; clients show "Message deleted" in its place.
 */
@Serializable
data class MessageTombstone(
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("time_created") val timeCreated: Long,
)

/** Message type for a local "Message deleted" placeholder (never sent to the server). */
const val DELETED_MESSAGE_TYPE: String = "deleted"

fun Message.isDeletedPlaceholder(): Boolean = messageType == DELETED_MESSAGE_TYPE

fun MessageTombstone.toPlaceholder(): Message =
    Message(
        id = messageId,
        user_id = userId,
        content = "",
        timeCreated = timeCreated,
        messageType = DELETED_MESSAGE_TYPE,
    )

/**
 * Timeline rows plus "Message deleted" placeholders for tombstones inside the loaded window.
 * A tombstone older than the oldest loaded message is left for when that page loads, so a
 * deletion far back in history never shows up detached at the top (iOS clips to the page span).
 */
fun withTombstonePlaceholders(
    messages: List<MessageWithUser>,
    tombstones: Collection<MessageTombstone>,
    userFor: (String) -> User,
    viewerUserId: String?,
): List<MessageWithUser> {
    if (tombstones.isEmpty()) return messages
    val present = messages.mapTo(HashSet()) { it.message.id }
    val oldest = messages.minOfOrNull { it.message.timeCreated } ?: return messages
    val placeholders =
        tombstones
            .filter { it.messageId !in present && it.timeCreated >= oldest }
            .map { MessageWithUser(it.toPlaceholder(), userFor(it.userId), isSent = it.userId == viewerUserId) }
    return if (placeholders.isEmpty()) messages else messages + placeholders
}
