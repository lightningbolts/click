package compose.project.click.click.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where one member has read up to in a chat (`chat_read_cursors.read_through`, epoch ms). */
@Serializable
data class ReadCursor(
    @SerialName("user_id") val userId: String,
    @SerialName("read_through") val readThrough: Long,
)

/**
 * Group "seen by" placement (iOS `ConversationModel` read cursors): each member (not the viewer,
 * not the message's own author) appears once, under the newest message they have read.
 * Returns message id → reader ids, readers in cursor order.
 */
fun seenByPlacement(
    messages: List<Message>,
    cursors: Map<String, Long>,
    viewerUserId: String?,
): Map<String, List<String>> {
    val ordered = messages.filterNot { it.id.startsWith("temp-") }.sortedBy { it.timeCreated }
    if (ordered.isEmpty()) return emptyMap()
    val placement = linkedMapOf<String, MutableList<String>>()
    cursors.entries
        .filter { it.key != viewerUserId }
        .sortedByDescending { it.value }
        .forEach { (reader, readThrough) ->
            val newestRead = ordered.lastOrNull { it.timeCreated <= readThrough && it.user_id != reader } ?: return@forEach
            placement.getOrPut(newestRead.id) { mutableListOf() }.add(reader)
        }
    return placement
}
